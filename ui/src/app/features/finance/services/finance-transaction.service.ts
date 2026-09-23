import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { JournalEntry, JournalLine, RecordExpenseRequest, RecordIncomeRequest, RecordTransferRequest } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

export interface TransactionFilter {
  fundId?: string;
  paymentMethod?: string;
  entryDateFrom?: string;
  entryDateTo?: string;
}

/**
 * Not a plain ResourceService<JournalEntry> — the Finance Phase 1 UI never creates a JournalEntry
 * directly (that would require picking debit/credit accounts by hand); it always goes through the
 * Record Income / Record Expense endpoints instead. See FinanceService#recordIncome/#recordExpense.
 */
@Injectable({ providedIn: 'root' })
export class FinanceTransactionService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(ENVIRONMENT);

  getPage(
    params: PageParams = DEFAULT_PAGE_PARAMS,
    filter: TransactionFilter = {},
  ): Observable<Page<JournalEntry>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    if (filter.fundId) {
      p = p.set('fundId', filter.fundId);
    }
    if (filter.paymentMethod) {
      p = p.set('paymentMethod', filter.paymentMethod);
    }
    if (filter.entryDateFrom) {
      p = p.set('entryDateFrom', filter.entryDateFrom);
    }
    if (filter.entryDateTo) {
      p = p.set('entryDateTo', filter.entryDateTo);
    }
    return this.http.get<Page<JournalEntry>>(
      `${this.env.apiUrl}/journal-entries`,
      { params: p },
    );
  }

  recordIncome(request: RecordIncomeRequest): Observable<JournalEntry> {
    return this.http.post<JournalEntry>(
      `${this.env.apiUrl}/income-transactions`,
      request,
    );
  }

  recordExpense(request: RecordExpenseRequest): Observable<JournalEntry> {
    return this.http.post<JournalEntry>(
      `${this.env.apiUrl}/expense-transactions`,
      request,
    );
  }

  recordTransfer(request: RecordTransferRequest): Observable<JournalEntry> {
    return this.http.post<JournalEntry>(
      `${this.env.apiUrl}/transfer-transactions`,
      request,
    );
  }

  /** The real double-entry lines behind an entry — e.g. reveals a processing-fee split
   *  (deposit net + fee expense + revenue gross) that the entry-level totalDebit alone hides. */
  getLines(entryId: string): Observable<JournalLine[]> {
    return this.http.get<JournalLine[]>(`${this.env.apiUrl}/journal-entries/${entryId}/lines`);
  }
}
