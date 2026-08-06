export function rowAmount(transaction: any): number {
  return Number(transaction?.amount ?? transaction?.transactionAmount) || 0;
}

export function rowStatus(transaction: any): string {
  return String(transaction?.status ?? transaction?.transactionStatus ?? 'PENDING').toUpperCase();
}

export function rowType(transaction: any): string {
  return String(transaction?.transactionType ?? transaction?.type ?? 'PURCHASE').toUpperCase();
}

export function cardLimit(card: any): number {
  return Number(card?.cardLimit ?? card?.creditLimit) || 0;
}

export function sameCard(card: any, transaction: any): boolean {
  const cardNumber = String(card?.cardNumber || '').replace(/\D/g, '');
  const transactionNumber = String(transaction?.cardNumber || '').replace(/\D/g, '');
  const cardId = String(card?.creditId ?? card?.id ?? '');
  const transactionCardId = String(transaction?.creditId ?? transaction?.cardId ?? '');
  return Boolean((cardNumber && transactionNumber && (cardNumber === transactionNumber || cardNumber.endsWith(transactionNumber) || transactionNumber.endsWith(cardNumber))) || (cardId && transactionCardId && cardId === transactionCardId));
}

export function transactionImpact(transaction: any): number {
  return ['PURCHASE', 'AUTHORIZATION'].includes(rowType(transaction)) ? rowAmount(transaction) : -rowAmount(transaction);
}

export function liveExposure(card: any, transactions: any[], statuses = ['PENDING', 'AUTHORIZED', 'COMPLETED']): number {
  return transactions.filter((transaction) => sameCard(card, transaction) && statuses.includes(rowStatus(transaction))).reduce((total, transaction) => total + transactionImpact(transaction), 0);
}

export function accountCards(card: any, cards: any[]): any[] {
  const customerId = String(card?.customerId ?? card?.custId ?? '');
  if (!customerId) return [card];
  const related = cards.filter((item) => String(item?.customerId ?? item?.custId ?? '') === customerId && item?.replacedByCreditId == null);
  return related.length ? related : [card];
}

export function accountExposure(card: any, cards: any[], transactions: any[], statuses = ['PENDING', 'AUTHORIZED', 'COMPLETED']): number {
  const numbers = new Set(accountCards(card, cards).map((item) => String(item?.cardNumber || '').replace(/\D/g, '')).filter(Boolean));
  return transactions.filter((transaction) => statuses.includes(rowStatus(transaction)) && numbers.has(String(transaction?.cardNumber || '').replace(/\D/g, ''))).reduce((total, transaction) => total + transactionImpact(transaction), 0);
}

export function heldAmount(card: any, transactions: any[], cards: any[] = [card]): number {
  return Math.max(0, accountExposure(card, cards, transactions, ['PENDING', 'AUTHORIZED']));
}

export function effectiveAvailable(card: any, transactions: any[], cards: any[] = [card]): number {
  const fromTransactions = Math.max(0, cardLimit(card) - accountExposure(card, cards, transactions));
  const values = accountCards(card, cards).map((item) => Number(item?.availableCredit ?? item?.availableBalance)).filter(Number.isFinite);
  const fromServer = values.length ? Math.min(...values) : Number(card?.availableCredit ?? card?.availableBalance);
  return Number.isFinite(fromServer) ? Math.min(Math.max(0, fromServer), fromTransactions) : fromTransactions;
}

export function canSettle(transaction: any, card: any, transactions: any[], cards: any[] = [card]): boolean {
  const settled = accountExposure(card, cards, transactions, ['COMPLETED']);
  return settled + transactionImpact(transaction) <= cardLimit(card);
}