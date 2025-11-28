package com.example.bidforstudy.data

import java.time.LocalDate
import java.time.LocalDateTime

data class AuctionKey(
    val roomNumber: String,
    val capacity: Int,
    val timeRange: String,
    val reservationDate: LocalDate
)

data class BidEntry(
    val bidderId: String,   // userId or "group:<joinCode>"
    val amount: Int,
    val timestamp: LocalDateTime
)

data class GroupMemberBid(
    val userId: String,
    var amount: Int
)

data class PendingGroupBid(
    val key: AuctionKey,
    val ownerId: String,
    val joinCode: String,         // we use ownerId as join code
    val capacity: Int,
    val members: MutableList<GroupMemberBid>,
    val isSecondChance: Boolean = false
)

data class FinalGroupBid(
    val key: AuctionKey,
    val groupId: String,          // "group:<joinCode>"
    val members: List<GroupMemberBid>,
    val totalAmount: Int
)

/**
 * Second-chance offer after a winner cancels.  Only used for single-person rooms.
 */
data class SecondChanceBid(
    val key: AuctionKey,
    val bidderId: String,
    val amount: Int
)

data class UserBidSummary(
    val auctionKey: AuctionKey,
    val amount: Int,              // your contribution or single bid
    val groupTotalAmount: Int?,   // null for single, total for group
    val isCurrentHighest: Boolean,
    val isActive: Boolean,
    val isGroup: Boolean
)

data class ReservationSummary(
    val auctionKey: AuctionKey,
    val amount: Int
)

data class BidResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val newCurrentBid: Int = 0
)

object BiddingManager {

    private val authRepo: AuthRepository = InMemoryAuthRepository

    // single + group final bids (amount = total)
    private val bidsByAuction = mutableMapOf<AuctionKey, MutableList<BidEntry>>()

    // pending groups: key (auction + owner) -> group (includes second-chance groups)
    private val pendingGroups =
        mutableMapOf<Pair<AuctionKey, String>, PendingGroupBid>() // (key, ownerId)

    // finalised group bids (for refunds + history)
    private val finalGroupBids = mutableListOf<FinalGroupBid>()

    // per-user group bid history (last 10)
    private val userGroupHistory =
        mutableMapOf<String, MutableList<UserGroupBidRecord>>()

    // second-chance offers for single-person rooms
    private val pendingSecondChances = mutableListOf<SecondChanceBid>()
    private val auctionsWithSecondChance = mutableSetOf<AuctionKey>()

    // (AuctionKey, id) where id is single userId OR group ownerId that already refused a second chance
    private val refusedSecondChances = mutableSetOf<Pair<AuctionKey, String>>()

    data class UserGroupBidRecord(
        val auctionKey: AuctionKey,
        val userAmount: Int,
        val groupTotal: Int,
        val timestamp: LocalDateTime
    )

    private fun getEndTime(key: AuctionKey): LocalDateTime {
        // Bid ends 1 week before reservation date at midnight
        return key.reservationDate.minusWeeks(1).atStartOfDay()
    }

    // ---------- common helpers ----------

    fun getCurrentBid(key: AuctionKey): Int {
        val bids = bidsByAuction[key] ?: return 0
        return bids.maxOfOrNull { it.amount } ?: 0
    }

    private fun getHighestBidEntry(key: AuctionKey): BidEntry? {
        val bids = bidsByAuction[key] ?: return null
        return bids.maxByOrNull { it.amount }
    }

    fun getLastBidsForAuction(key: AuctionKey, limit: Int = 5): List<BidEntry> {
        return bidsByAuction[key]
            ?.sortedByDescending { it.timestamp }
            ?.take(limit)
            ?: emptyList()
    }

    // ---------- SINGLE-PERSON BIDDING ----------

    fun placeSingleBid(
        key: AuctionKey,
        bidderId: String,
        amount: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): BidResult {
        if (key.capacity != 1) {
            return BidResult(
                success = false,
                errorMessage = "This is not a single-person room."
            )
        }

        val endTime = getEndTime(key)
        if (!now.isBefore(endTime)) {
            return BidResult(
                success = false,
                errorMessage = "Bidding has ended for this reservation."
            )
        }

        if (amount <= 0) {
            return BidResult(
                success = false,
                errorMessage = "Bid amount must be positive."
            )
        }

        val currentBid = getCurrentBid(key)
        if (amount <= currentBid) {
            return BidResult(
                success = false,
                errorMessage = "Bid must be higher than the current bid ($currentBid tokens)."
            )
        }

        val userTokens = authRepo.getTokens(bidderId)
        if (amount > userTokens) {
            return BidResult(
                success = false,
                errorMessage = "You don't have enough tokens."
            )
        }

        val list = bidsByAuction.getOrPut(key) { mutableListOf() }

        // Refund previous highest (single or group)
        val previousHighest = getHighestBidEntry(key)
        if (previousHighest != null) {
            refundBid(previousHighest)
        }

        // Deduct new bidder's tokens
        authRepo.addTokens(bidderId, -amount)

        // Record new bid
        list.add(
            BidEntry(
                bidderId = bidderId,
                amount = amount,
                timestamp = now
            )
        )

        return BidResult(success = true, newCurrentBid = amount)
    }

    // refund for either single or group
    private fun refundBid(bid: BidEntry) {
        val bidderId = bid.bidderId
        if (bidderId.startsWith("group:")) {
            val groupId = bidderId
            val fg = finalGroupBids.lastOrNull {
                it.groupId == groupId && it.totalAmount == bid.amount
            } ?: return
            fg.members.forEach { member ->
                authRepo.addTokens(member.userId, member.amount)
            }
        } else {
            authRepo.addTokens(bidderId, bid.amount)
        }
    }

    // ---------- GROUP BIDDING (MULTI-PERSON ROOMS) ----------

    fun startGroupBid(
        key: AuctionKey,
        ownerId: String,
        amount: Int
    ): BidResult {
        if (key.capacity <= 1) {
            return BidResult(
                success = false,
                errorMessage = "Group bidding is only for multi-person rooms."
            )
        }
        if (amount <= 0) {
            return BidResult(
                success = false,
                errorMessage = "Bid amount must be positive."
            )
        }
        val endTime = getEndTime(key)
        if (!LocalDateTime.now().isBefore(endTime)) {
            return BidResult(
                success = false,
                errorMessage = "Bidding has ended for this reservation."
            )
        }

        val pairKey = key to ownerId
        if (pendingGroups.containsKey(pairKey)) {
            return BidResult(
                success = false,
                errorMessage = "You already started a group for this room and time."
            )
        }

        val joinCode = ownerId
        val group = PendingGroupBid(
            key = key,
            ownerId = ownerId,
            joinCode = joinCode,
            capacity = key.capacity,
            members = mutableListOf(GroupMemberBid(ownerId, amount))
        )
        pendingGroups[pairKey] = group

        return BidResult(success = true)
    }

    fun joinGroupBid(
        key: AuctionKey,
        joinCode: String,
        userId: String,
        amount: Int
    ): BidResult {
        if (amount <= 0) {
            return BidResult(
                success = false,
                errorMessage = "Bid amount must be positive."
            )
        }

        val pairKey = key to joinCode
        val group = pendingGroups[pairKey]
            ?: return BidResult(success = false, errorMessage = "Group not found.")

        if (group.isSecondChance) {
            return BidResult(
                success = false,
                errorMessage = "You can't join a second-chance group bid."
            )
        }

        val existing = group.members.find { it.userId == userId }
        if (existing == null) {
            if (group.members.size >= group.capacity) {
                return BidResult(
                    success = false,
                    errorMessage = "This group is full (capacity reached)."
                )
            }
            group.members.add(GroupMemberBid(userId, amount))
            return BidResult(success = true)
        } else {
            return BidResult(
                success = false,
                errorMessage = "You are already in this group. Use change bid instead."
            )
        }
    }

    fun updateGroupMemberBid(
        key: AuctionKey,
        joinCode: String,
        userId: String,
        newAmount: Int
    ): BidResult {
        if (newAmount <= 0) {
            return BidResult(
                success = false,
                errorMessage = "Bid amount must be positive."
            )
        }
        val pairKey = key to joinCode
        val group = pendingGroups[pairKey]
            ?: return BidResult(success = false, errorMessage = "Group not found.")

        if (group.isSecondChance) {
            return BidResult(
                success = false,
                errorMessage = "You cannot change bids in a second-chance group. Submit or cancel instead."
            )
        }

        val member = group.members.find { it.userId == userId }
            ?: return BidResult(success = false, errorMessage = "You are not in this group.")
        member.amount = newAmount
        return BidResult(success = true)
    }

    fun cancelGroupBid(
        key: AuctionKey,
        joinCode: String,
        requesterId: String
    ): BidResult {
        val pairKey = key to joinCode
        val group = pendingGroups[pairKey]
            ?: return BidResult(success = false, errorMessage = "Group not found.")

        if (group.ownerId != requesterId) {
            return BidResult(
                success = false,
                errorMessage = "Only the group owner can cancel the group."
            )
        }

        pendingGroups.remove(pairKey)

        if (!group.isSecondChance) {
            return BidResult(success = true)
        }

        // Second-chance group cancelled: mark refused for this owner,
        // and try to offer the next highest group a second chance.
        refusedSecondChances.add(key to group.ownerId)

        val created = offerNextGroupSecondChance(key)
        if (!created) {
            auctionsWithSecondChance.remove(key)
        }

        return BidResult(success = true)
    }

    fun submitGroupBid(
        key: AuctionKey,
        joinCode: String,
        requesterId: String,
        now: LocalDateTime = LocalDateTime.now()
    ): BidResult {
        val pairKey = key to joinCode
        val group = pendingGroups[pairKey]
            ?: return BidResult(success = false, errorMessage = "Group not found.")
        if (group.ownerId != requesterId) {
            return BidResult(
                success = false,
                errorMessage = "Only the group owner can submit the group bid."
            )
        }

        val endTime = getEndTime(key)
        if (!now.isBefore(endTime)) {
            return BidResult(
                success = false,
                errorMessage = "Bidding has ended for this reservation."
            )
        }

        val total = group.members.sumOf { it.amount }
        if (total <= 0) {
            return BidResult(success = false, errorMessage = "Total bid must be positive.")
        }

        val currentBid = getCurrentBid(key)
        // For normal groups, they must beat current bid.
        // For second-chance groups, they can match their previous total (currentBid).
        if (!group.isSecondChance && total <= currentBid) {
            return BidResult(
                success = false,
                errorMessage = "Group total ($total) must be higher than current bid ($currentBid)."
            )
        }

        // Check every member has enough tokens
        for (m in group.members) {
            val tokens = authRepo.getTokens(m.userId)
            if (m.amount > tokens) {
                return BidResult(
                    success = false,
                    errorMessage = "User ${m.userId} does not have enough tokens."
                )
            }
        }

        val list = bidsByAuction.getOrPut(key) { mutableListOf() }

        // Refund previous highest (single or group)
        val previousHighest = getHighestBidEntry(key)
        if (previousHighest != null) {
            refundBid(previousHighest)
        }

        // Deduct tokens from each member
        group.members.forEach { m ->
            authRepo.addTokens(m.userId, -m.amount)
        }

        // Record as group bid
        val groupId = "group:${group.ownerId}"
        val entry = BidEntry(
            bidderId = groupId,
            amount = total,
            timestamp = now
        )
        list.add(entry)

        // Save final group info for possible refunds later
        finalGroupBids.add(
            FinalGroupBid(
                key = key,
                groupId = groupId,
                members = group.members.map { it.copy() },
                totalAmount = total
            )
        )

        // Add to user group bid history
        group.members.forEach { m ->
            val h = userGroupHistory.getOrPut(m.userId) { mutableListOf() }
            h.add(
                0,
                UserGroupBidRecord(
                    auctionKey = key,
                    userAmount = m.amount,
                    groupTotal = total,
                    timestamp = now
                )
            )
            if (h.size > 10) h.removeLast()
        }

        // Remove pending group
        pendingGroups.remove(pairKey)

        if (group.isSecondChance) {
            auctionsWithSecondChance.remove(key)
        }

        return BidResult(success = true, newCurrentBid = total)
    }

    fun getPendingGroupsForUser(userId: String): List<PendingGroupBid> {
        return pendingGroups.values.filter { group ->
            group.members.any { it.userId == userId }
        }
    }

    fun getPendingGroup(
        key: AuctionKey,
        joinCode: String
    ): PendingGroupBid? {
        return pendingGroups[key to joinCode]
    }

    // ---------- SECOND-CHANCE HELPERS FOR GROUPS ----------

    /**
     * Offer the next highest group (different owner, not refused) a second-chance pending bid.
     */
    private fun offerNextGroupSecondChance(key: AuctionKey): Boolean {
        val bids = bidsByAuction[key] ?: return false

        val candidatePair = bids
            .filter { it.bidderId.startsWith("group:") }
            .map { entry ->
                val ownerId = entry.bidderId.removePrefix("group:")
                ownerId to entry
            }
            .filter { (ownerId, _) -> (key to ownerId) !in refusedSecondChances }
            .maxByOrNull { (_, entry) -> entry.amount }
            ?: return false

        val (ownerId, entry) = candidatePair

        val fg = finalGroupBids.lastOrNull {
            it.key == key && it.groupId == entry.bidderId && it.totalAmount == entry.amount
        } ?: return false

        val pending = PendingGroupBid(
            key = key,
            ownerId = ownerId,
            joinCode = ownerId,
            capacity = key.capacity,
            members = fg.members.map { GroupMemberBid(it.userId, it.amount) }.toMutableList(),
            isSecondChance = true
        )

        pendingGroups[key to ownerId] = pending
        auctionsWithSecondChance.add(key)
        return true
    }

    // ---------- SECOND-CHANCE BIDS AFTER CANCELLATION (SINGLE ROOMS) ----------

    fun cancelReservationForUser(
        key: AuctionKey,
        userId: String,
        amount: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): BidResult {
        if (key.capacity != 1) {
            return BidResult(
                success = false,
                errorMessage = "Cancellation with second chance is only implemented for single-person rooms."
            )
        }

        // Must be at least one day before reservation date
        val latestCancelTime = key.reservationDate.minusDays(1).atStartOfDay()
        if (!now.isBefore(latestCancelTime)) {
            return BidResult(
                success = false,
                errorMessage = "You can only cancel at least one day before the reservation date."
            )
        }

        val list = bidsByAuction[key]
            ?: return BidResult(success = false, errorMessage = "No bids found for this reservation.")

        val top = getHighestBidEntry(key)
            ?: return BidResult(success = false, errorMessage = "No winning bid found.")

        // Only allow cancellation if the current overall winner is this single user
        if (top.bidderId.startsWith("group:") ||
            top.bidderId != userId || top.amount != amount
        ) {
            return BidResult(
                success = false,
                errorMessage = "You are not the current winner for this reservation."
            )
        }

        // Refund 50% of placed tokens
        val refund = amount / 2
        if (refund > 0) {
            authRepo.addTokens(userId, refund)
        }

        // Remove ALL single-person bids from this user for this auction
        list.removeIf { !it.bidderId.startsWith("group:") && it.bidderId == userId }

        // Mark that this auction is in "second chance" mode
        auctionsWithSecondChance.add(key)

        // Choose the next highest single-person bidder
        val candidate = list
            .filter {
                !it.bidderId.startsWith("group:") &&
                        it.bidderId != userId &&
                        (key to it.bidderId) !in refusedSecondChances
            }
            .maxByOrNull { it.amount }

        if (candidate != null) {
            pendingSecondChances.removeIf {
                it.key == key && it.bidderId == candidate.bidderId
            }
            pendingSecondChances.add(
                SecondChanceBid(
                    key = key,
                    bidderId = candidate.bidderId,
                    amount = candidate.amount
                )
            )
        }

        return BidResult(success = true)
    }

    fun getSecondChanceBidsForUser(userId: String): List<SecondChanceBid> {
        return pendingSecondChances.filter { it.bidderId == userId }
    }

    fun submitSecondChanceBid(
        key: AuctionKey,
        userId: String
    ): BidResult {
        val sc = pendingSecondChances.firstOrNull {
            it.key == key && it.bidderId == userId
        } ?: return BidResult(success = false, errorMessage = "No pending offer found.")

        val needed = sc.amount
        val tokens = authRepo.getTokens(userId)
        if (tokens < needed) {
            return BidResult(
                success = false,
                errorMessage = "You don't have enough tokens to accept this offer."
            )
        }

        // Deduct tokens
        authRepo.addTokens(userId, -needed)

        // Remove pending offer and mark auction as resolved
        pendingSecondChances.remove(sc)
        auctionsWithSecondChance.remove(key)

        return BidResult(success = true)
    }

    fun cancelSecondChanceBid(
        key: AuctionKey,
        userId: String
    ): BidResult {
        val sc = pendingSecondChances.firstOrNull {
            it.key == key && it.bidderId == userId
        } ?: return BidResult(success = false, errorMessage = "No pending offer found.")

        pendingSecondChances.remove(sc)
        refusedSecondChances.add(key to userId)

        // Offer to next highest bidder if any
        val list = bidsByAuction[key] ?: emptyList()
        val nextCandidate = list
            .filter {
                !it.bidderId.startsWith("group:") &&
                        (key to it.bidderId) !in refusedSecondChances
            }
            .maxByOrNull { it.amount }

        if (nextCandidate != null) {
            pendingSecondChances.add(
                SecondChanceBid(
                    key = key,
                    bidderId = nextCandidate.bidderId,
                    amount = nextCandidate.amount
                )
            )
        } else {
            // No more candidates
            auctionsWithSecondChance.remove(key)
        }

        return BidResult(success = true)
    }

    // ---------- GROUP RESERVATION CANCELLATION (FINAL RESERVATION) ----------

    /**
     * Cancel a FINAL group reservation (winner is a group).
     * Only the group owner can do this. Each member gets 50% refund, and
     * the next highest group may receive a second-chance pending group.
     */
    fun cancelGroupReservationForOwner(
        key: AuctionKey,
        ownerId: String,
        now: LocalDateTime = LocalDateTime.now()
    ): BidResult {
        if (key.capacity <= 1) {
            return BidResult(
                success = false,
                errorMessage = "Group reservation cancellation is only for multi-person rooms."
            )
        }

        // Must be at least one day before reservation date
        val latestCancelTime = key.reservationDate.minusDays(1).atStartOfDay()
        if (!now.isBefore(latestCancelTime)) {
            return BidResult(
                success = false,
                errorMessage = "You can only cancel at least one day before the reservation date."
            )
        }

        val list = bidsByAuction[key]
            ?: return BidResult(success = false, errorMessage = "No bids found for this reservation.")

        val top = getHighestBidEntry(key)
            ?: return BidResult(success = false, errorMessage = "No winning bid found.")

        val expectedGroupId = "group:$ownerId"
        if (top.bidderId != expectedGroupId) {
            return BidResult(
                success = false,
                errorMessage = "You are not the current winning group owner for this reservation."
            )
        }

        val fg = finalGroupBids.lastOrNull {
            it.key == key && it.groupId == expectedGroupId && it.totalAmount == top.amount
        } ?: return BidResult(false, "Group details not found for this reservation.")

        // Refund 50% to each member
        fg.members.forEach { m ->
            val refund = m.amount / 2
            if (refund > 0) {
                authRepo.addTokens(m.userId, refund)
            }
        }

        // Remove winning group entry from the auction list
        list.remove(top)

        // Mark second-chance mode
        auctionsWithSecondChance.add(key)

        // Try to offer second chance to the next group
        val created = offerNextGroupSecondChance(key)
        if (!created) {
            // No second-chance group
            auctionsWithSecondChance.remove(key)
        }

        return BidResult(success = true)
    }

    // ---------- USER HISTORY ----------

    fun getUserBidHistory(
        userId: String,
        limit: Int = 10
    ): List<UserBidSummary> {
        val now = LocalDateTime.now()
        val singles = mutableListOf<UserBidSummary>()

        bidsByAuction.forEach { (key, list) ->
            list.filter { it.bidderId == userId }.forEach { bid ->
                val endTime = getEndTime(key)
                val isActive = now.isBefore(endTime)
                val current = getCurrentBid(key)
                val isCurrentHighest = (bid.amount == current) && isActive
                singles += UserBidSummary(
                    auctionKey = key,
                    amount = bid.amount,
                    groupTotalAmount = null,
                    isCurrentHighest = isCurrentHighest,
                    isActive = isActive,
                    isGroup = false
                )
            }
        }

        val groups = userGroupHistory[userId].orEmpty().map { rec ->
            val endTime = getEndTime(rec.auctionKey)
            val isActive = now.isBefore(endTime)
            val current = getCurrentBid(rec.auctionKey)
            val isCurrentHighest = (rec.groupTotal == current) && isActive
            UserBidSummary(
                auctionKey = rec.auctionKey,
                amount = rec.userAmount,
                groupTotalAmount = rec.groupTotal,
                isCurrentHighest = isCurrentHighest,
                isActive = isActive,
                isGroup = true
            )
        }

        val all = (singles + groups).sortedByDescending {
            when {
                it.isGroup -> userGroupHistory[userId]?.find { r -> r.auctionKey == it.auctionKey }?.timestamp
                    ?: LocalDateTime.MIN
                else -> LocalDateTime.MIN
            }
        }

        return all.take(limit)
    }

    fun getUserReservationHistory(
        userId: String,
        limit: Int = 10
    ): List<ReservationSummary> {
        val now = LocalDateTime.now()
        val results = mutableListOf<ReservationSummary>()

        bidsByAuction.forEach { (key, list) ->
            if (list.isEmpty()) return@forEach

            val endTime = getEndTime(key)
            if (now.isBefore(endTime)) return@forEach

            if (key in auctionsWithSecondChance) return@forEach

            val top = list.maxByOrNull { it.amount } ?: return@forEach

            if (!top.bidderId.startsWith("group:")) {
                // single person winner
                if (top.bidderId == userId) {
                    results += ReservationSummary(
                        auctionKey = key,
                        amount = top.amount
                    )
                }
            } else {
                // group winner
                val groupId = top.bidderId
                val fg = finalGroupBids.lastOrNull {
                    it.groupId == groupId && it.key == key && it.totalAmount == top.amount
                } ?: return@forEach
                if (fg.members.any { it.userId == userId }) {
                    results += ReservationSummary(
                        auctionKey = key,
                        amount = top.amount
                    )
                }
            }
        }

        return results
            .sortedByDescending { it.auctionKey.reservationDate }
            .take(limit)
    }
}
