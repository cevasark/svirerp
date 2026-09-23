import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { Account } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class AccountService extends ResourceService<Account> {
  private readonly orgScopedEnv = inject(ENVIRONMENT);

  constructor() {
    super('accounts');
  }

  /** List is org-scoped (unlike get/create/update/delete, which are flat). Lazily seeds a default
   *  chart of accounts on the org's first request — see FinanceService#seedDefaultChartOfAccounts. */
  override getPage(params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<Account>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    return this.http.get<Page<Account>>(
      `${this.orgScopedEnv.apiUrl}/accounts`,
      { params: p },
    );
  }
}
