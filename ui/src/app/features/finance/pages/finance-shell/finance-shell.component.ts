import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'app-finance-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatTabsModule],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="tabPanel" class="finance-tabs">
      <a mat-tab-link routerLink="reports" routerLinkActive #reportsActive="routerLinkActive"
         [active]="reportsActive.isActive">
        Reports
      </a>
      <a mat-tab-link routerLink="transactions" routerLinkActive #transactionsActive="routerLinkActive"
         [active]="transactionsActive.isActive">
        Transactions
      </a>
      <a mat-tab-link routerLink="service-requests" routerLinkActive #serviceRequestsActive="routerLinkActive"
         [active]="serviceRequestsActive.isActive">
        Service Requests
      </a>
      <a mat-tab-link routerLink="zeffy-import" routerLinkActive #zeffyImportActive="routerLinkActive"
         [active]="zeffyImportActive.isActive">
        Zeffy Import
      </a>
      <a mat-tab-link routerLink="stripe" routerLinkActive #stripeActive="routerLinkActive"
         [active]="stripeActive.isActive">
        Stripe
      </a>
      <a mat-tab-link routerLink="master" routerLinkActive #masterActive="routerLinkActive"
         [active]="masterActive.isActive">
        Master
      </a>
      <a mat-tab-link routerLink="vendors" routerLinkActive #vendorsActive="routerLinkActive"
         [active]="vendorsActive.isActive">
        Vendors
      </a>
    </nav>
    <mat-tab-nav-panel #tabPanel>
      <router-outlet />
    </mat-tab-nav-panel>
  `,
  styles: [`
    .finance-tabs { margin-bottom: 8px; }
  `],
})
export class FinanceShellComponent {}
