package com.project;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.project.CreditCards.CardName;
import com.project.DTO.PeriodCountDTO;

@Service
public class CreditService {

	private final CreditRepository cr;
    private final RestTemplate rtCards;
	
	public CreditService(CreditRepository cr,@Qualifier("restCardTemplate") RestTemplate rtCards) {
        this.rtCards = rtCards;
        this.cr=cr;
    }
	
	public List<CreditCards> getAllCards() {
        deactivateExpiredCards();
		List<CreditCards> allCards = new ArrayList<>();
		cr.findAll().forEach(allCards::add);
		return allCards;
	}
	
	public CreditCards getCardById(Integer id) {
		return cr.findById(id).orElse(null);
	}
	
	public CreditCards addCard(CreditCards card) {
        if (card.getCustomerId() == null || card.getCardHolderName() == null || card.getCardHolderName().isBlank()) throw new IllegalArgumentException("Customer ID and cardholder name are required");
        if (card.getCardName() == null || card.getCardType() == null) throw new IllegalArgumentException("Card name and card type are required");
        List<CreditCards> accountCards = cr.findByCustomerId(card.getCustomerId()).stream().filter(existing -> existing.getReplacedByCreditId() == null).toList();
        if (accountCards.stream().anyMatch(existing -> existing.getCardType() == card.getCardType())) throw new IllegalArgumentException("A customer can have only one PRIMARY and one ADD_ON card");
        if (card.getExpiryDate() == null || card.getDueDate() == null || !card.getDueDate().isBefore(card.getExpiryDate())) throw new IllegalArgumentException("Due date must be before expiry date");
        if (!accountCards.isEmpty()) {
            CreditCards account = accountCards.get(0);
            if (account.getCardName() != card.getCardName()) throw new IllegalArgumentException("Primary and add-on cards must share the same card tier");
            card.setCardLimit(account.getCardLimit());
            card.setAvailableCredit(account.getAvailableCredit());
            card.setDueDate(account.getDueDate());
            card.setExpiryDate(account.getExpiryDate());
        } else {
            double limit = limitFor(card.getCardName());
            card.setCardLimit(limit);
            card.setAvailableCredit(limit);
        }
        card.setCardNumber(generateCardNumber());
        card.setStatus(CardStatus.ACTIVE);
        return cr.save(card);
    }
    /** Expiry is terminal for a physical card. A renewal creates a new active card instead. */
    private void deactivateExpiredCards() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<CreditCards> expired = cr.findAll().stream().filter(card -> card.getExpiryDate() != null && card.getExpiryDate().isBefore(today)).filter(card -> card.getStatus() != CardStatus.INACTIVE).toList();
        expired.forEach(card -> card.setStatus(CardStatus.INACTIVE));
        if (!expired.isEmpty()) cr.saveAll(expired);
    }
    private boolean isExpired(CreditCards card) { return card.getExpiryDate() != null && card.getExpiryDate().isBefore(LocalDate.now(ZoneId.of("Asia/Kolkata"))); }
    /** Replaces every current physical card on the account (primary and add-on) as one renewal event. */
    @org.springframework.transaction.annotation.Transactional
    public List<CreditCards> renewCard(Integer cardId, LocalDate expiryDate, LocalDate dueDate) {
        CreditCards requestedCard = cr.findById(cardId).orElseThrow(() -> new IllegalArgumentException("Card not found"));
        if (requestedCard.getReplacedByCreditId() != null) throw new IllegalStateException("This card has already been replaced");
        if (expiryDate == null || dueDate == null || !dueDate.isBefore(expiryDate)) throw new IllegalArgumentException("Due date must be before expiry date");
        if (requestedCard.getExpiryDate() != null && expiryDate.isBefore(requestedCard.getExpiryDate())) throw new IllegalArgumentException("Renewal expiry date cannot be earlier than the current expiry date");
        List<CreditCards> currentCards = cr.findByCustomerIdForUpdate(requestedCard.getCustomerId()).stream()
            .filter(card -> card.getReplacedByCreditId() == null).toList();
        if (currentCards.isEmpty()) throw new IllegalStateException("No current cards are available to renew");
        CreditCards account = currentCards.get(0);
        List<CreditCards> renewed = new ArrayList<>();
        for (CreditCards oldCard : currentCards) {
            CreditCards replacement = new CreditCards();
            replacement.setCustomerId(oldCard.getCustomerId());
            replacement.setCardName(account.getCardName());
            replacement.setCardType(oldCard.getCardType());
            replacement.setCardLimit(account.getCardLimit());
            replacement.setAvailableCredit(account.getAvailableCredit());
            replacement.setCardHolderName(oldCard.getCardHolderName());
            replacement.setExpiryDate(expiryDate);
            replacement.setDueDate(dueDate);
            replacement.setCardNumber(generateCardNumber());
            replacement.setStatus(CardStatus.ACTIVE);
            replacement.setReplacementOfCreditId(oldCard.getCreditId());
            replacement = cr.save(replacement);
            oldCard.setStatus(CardStatus.INACTIVE);
            oldCard.setReplacedByCreditId(replacement.getCreditId());
            cr.save(oldCard);
            renewed.add(replacement);
        }
        return renewed;
    }    private double limitFor(CardName name) { return switch (name) { case SILVER -> 50000d; case GOLD -> 150000d; case PLATINUM -> 300000d; case ULTRA_PREMIUM -> 500000d; }; }
        private Long generateCardNumber() { java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current(); Long number; do { number = random.nextLong(100_000_000_000L, 1_000_000_000_000L); } while (cr.existsByCardNumber(number)); return number; }
 
	
	public Page<CreditCards> getAllCardsByPagination(int page, int size) {
		   Pageable pageable = PageRequest.of(
		            page,
		            size,
		            Sort.by("creditId").ascending()
		    );

		    return cr.findAll(pageable);
	    }
	
    @org.springframework.transaction.annotation.Transactional
	public CreditCards putCard(CreditCards card, Integer id) {
        CreditCards existingCard = cr.findById(id).orElseThrow(() -> new RuntimeException("Card not found"));
        CardName sharedName = card.getCardName() == null ? existingCard.getCardName() : card.getCardName();
        double sharedLimit = limitFor(sharedName);
        double outstanding = Math.max(0d, existingCard.getCardLimit() - existingCard.getAvailableCredit());
        if (outstanding > sharedLimit) throw new IllegalArgumentException("The selected card tier is lower than the current shared balance");
        double sharedAvailable = sharedLimit - outstanding;
        LocalDate sharedExpiry = card.getExpiryDate() == null ? existingCard.getExpiryDate() : card.getExpiryDate();
        LocalDate sharedDue = card.getDueDate() == null ? existingCard.getDueDate() : card.getDueDate();
        if (sharedExpiry != null && sharedDue != null && !sharedDue.isBefore(sharedExpiry)) {
            throw new IllegalArgumentException("Due date must be before expiry date");
        }
        List<CreditCards> accountCards = cr.findByCustomerId(existingCard.getCustomerId()).stream().filter(accountCard -> accountCard.getReplacedByCreditId() == null).toList();
        accountCards.forEach(accountCard -> {
            accountCard.setCardName(sharedName);
            accountCard.setCardLimit(sharedLimit);
            accountCard.setAvailableCredit(sharedAvailable);
            accountCard.setExpiryDate(sharedExpiry);
            accountCard.setDueDate(sharedDue);
        });
        if (card.getCardHolderName() != null && !card.getCardHolderName().isBlank()) {
            existingCard.setCardHolderName(card.getCardHolderName());
        }
        cr.saveAll(accountCards);
        return existingCard;
    }
    
	
	@org.springframework.transaction.annotation.Transactional
    public CreditCards patchCard(Map<String, String> card, Integer id) {

	    CreditCards existingCard = cr.findById(id)
	            .orElseThrow(() -> new RuntimeException("Card not found"));

	    if (card.containsKey("availableCredit")) {
	        existingCard.setAvailableCredit(Double.parseDouble(card.get("availableCredit")));
	    }

	    if (card.containsKey("cardHolderName")) {
	        existingCard.setCardHolderName(card.get("cardHolderName"));
	    }

	    if (card.containsKey("cardLimit")) {
	        existingCard.setCardLimit(Double.parseDouble(card.get("cardLimit")));
	    }

	    if (card.containsKey("cardName")) {
	        existingCard.setCardName(CardName.valueOf(card.get("cardName").toUpperCase()));
	    }

	    if (card.containsKey("cardType")) {
	        existingCard.setCardType(CardType.valueOf(card.get("cardType").toUpperCase()));
	    }

	    if (card.containsKey("expiryDate")) {
	        existingCard.setExpiryDate(LocalDate.parse(card.get("expiryDate")));
	    }

	    if (card.containsKey("status")) {
        existingCard.setStatus(CardStatus.valueOf(card.get("status").toUpperCase()));
    }
    if (card.containsKey("dueDate")) {
        existingCard.setDueDate(LocalDate.parse(card.get("dueDate")));
    }

    if (existingCard.getExpiryDate() != null && existingCard.getDueDate() != null
            && !existingCard.getDueDate().isBefore(existingCard.getExpiryDate())) {
        throw new IllegalArgumentException("Due date must be before expiry date");
    }
    if (card.containsKey("expiryDate") || card.containsKey("dueDate")) {
        List<CreditCards> accountCards = cr.findByCustomerId(existingCard.getCustomerId()).stream().filter(accountCard -> accountCard.getReplacedByCreditId() == null).toList();
        accountCards.forEach(accountCard -> {
            accountCard.setExpiryDate(existingCard.getExpiryDate());
            accountCard.setDueDate(existingCard.getDueDate());
        });
        cr.saveAll(accountCards);
        return existingCard;
    }
    return cr.save(existingCard);
	}
	
	
	 public String deleteCard(Integer cardId) {
		 cr.deleteById(cardId);
		 return "The cutsomer with id:"+cardId+" is deleted successfully";
	 }
	
	 public List<CreditCards> getAllCardsOfCustomerById(Integer customerId) {
		    return cr.findByCustomerId(customerId);
		}
	 
	
	 public List<PeriodCountDTO> getCardsCountByMonth() {
		    return mapPeriodCounts(cr.countGroupedByMonth());
		}

		public List<PeriodCountDTO> getCardsCountByWeek() {
		    return mapPeriodCounts(cr.countGroupedByWeek());
		}

		public List<PeriodCountDTO> getCardsCountByYear() {
		    return mapPeriodCounts(cr.countGroupedByYear());
		}

		
		public List<PeriodCountDTO> getCardsCountByPeriod(String type) {
		    List<Object[]> rows;

		    switch (type.toLowerCase()) {
		        case "month" -> rows = cr.countGroupedByMonth();
		        case "week"  -> rows = cr.countGroupedByWeek();
		        case "year"  -> rows = cr.countGroupedByYear();
		        default -> throw new IllegalArgumentException("Invalid type. Use month, week, or year.");
		    }

		    return mapPeriodCounts(rows);
		}
		private List<PeriodCountDTO> mapPeriodCounts(List<Object[]> rows) {
		    List<PeriodCountDTO> result = new ArrayList<>();

		    for (Object[] row : rows) {
		        result.add(new PeriodCountDTO(
		                (String) row[0],
		                ((Number) row[1]).longValue()
		        ));
		    }

		    return result;
		}
	
    public void deactivateCardsForCustomer(Integer customerId) {
        List<CreditCards> cards = cr.findByCustomerId(customerId);
        cards.forEach(card -> card.setStatus(CardStatus.INACTIVE));
        cr.saveAll(cards);
    }
    public CreditCards advanceDueDate(Long cardNumber) {
        CreditCards card = cr.findByCardNumber(cardNumber).orElseThrow(() -> new RuntimeException("Card not found"));
        if (card.getDueDate() == null) throw new IllegalStateException("Card due date is unavailable");
        LocalDate nextDueDate = card.getDueDate().plusMonths(1);
        List<CreditCards> accountCards = cr.findByCustomerId(card.getCustomerId()).stream().filter(existing -> existing.getReplacedByCreditId() == null).toList();
        accountCards.forEach(accountCard -> accountCard.setDueDate(nextDueDate));
        cr.saveAll(accountCards);
        return card;
    }
    /** Applies balance changes to the current physical card pair, including settlements for a transaction made before renewal. */
    @org.springframework.transaction.annotation.Transactional
    public CreditCards adjustAvailableCredit(Long cardNumber, Double delta) {
        CreditCards requested = cr.findByCardNumberForUpdate(cardNumber).orElseThrow(() -> new RuntimeException("Card not found"));
        List<CreditCards> currentCards = cr.findByCustomerIdForUpdate(requested.getCustomerId()).stream()
                .filter(accountCard -> accountCard.getReplacedByCreditId() == null).toList();
        CreditCards card = currentCards.stream()
                .filter(accountCard -> accountCard.getCardType() == requested.getCardType())
                .findFirst().orElse(requested);
        if (card.getStatus() != CardStatus.ACTIVE || isExpired(card)) throw new IllegalStateException("Expired, inactive, or blocked cards cannot transact");
        double next = card.getAvailableCredit() + delta;
        if (next < 0 || next > card.getCardLimit()) throw new IllegalArgumentException("Credit adjustment is invalid");
        currentCards.forEach(accountCard -> {
            accountCard.setCardName(card.getCardName());
            accountCard.setCardLimit(card.getCardLimit());
            accountCard.setAvailableCredit(next);
        });
        cr.saveAll(currentCards);
        return card;
    }    @jakarta.annotation.PostConstruct
    public void backfillMissingCardFields() {
        cr.findAll().forEach(card -> {
            if (card.getCardLimit() == null) { double limit = switch (card.getCardName()) { case SILVER -> 50000d; case GOLD -> 150000d; case PLATINUM -> 300000d; case ULTRA_PREMIUM -> 500000d; }; card.setCardLimit(limit); }
            if (card.getAvailableCredit() == null) card.setAvailableCredit(card.getCardLimit());
            if (card.getCardNumber() == null) card.setCardNumber(generateCardNumber());
            if (card.getStatus() == null) card.setStatus(CardStatus.ACTIVE);
            cr.save(card);
        });
        cr.findAll().stream().filter(card -> card.getReplacedByCreditId() == null).collect(java.util.stream.Collectors.groupingBy(CreditCards::getCustomerId)).values().forEach(accountCards -> {
            if (accountCards.size() < 2) return;
            CreditCards account = accountCards.stream().filter(card -> card.getCardType() == CardType.PRIMARY).findFirst().orElse(accountCards.get(0));
            accountCards.forEach(card -> {
                card.setCardName(account.getCardName());
                card.setCardLimit(account.getCardLimit());
                card.setAvailableCredit(account.getAvailableCredit());
                card.setDueDate(account.getDueDate());
                card.setExpiryDate(account.getExpiryDate());
            });
            cr.saveAll(accountCards);
        });
    }
}
