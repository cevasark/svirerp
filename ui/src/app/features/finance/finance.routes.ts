import { Routes } from '@angular/router';

export const financeRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/finance-shell/finance-shell.component').then(m => m.FinanceShellComponent),
    children: [
      { path: '', redirectTo: 'transactions', pathMatch: 'full' },
      {
        path: 'transactions',
        loadComponent: () =>
          import('./pages/transaction-list/transaction-list.component').then(
            m => m.TransactionListComponent,
          ),
      },
      {
        path: 'master',
        loadComponent: () =>
          import('./pages/master-shell/master-shell.component').then(m => m.MasterShellComponent),
        children: [
          { path: '', redirectTo: 'projects', pathMatch: 'full' },
          {
            path: 'projects',
            loadComponent: () =>
              import('./pages/fund-list/fund-list.component').then(m => m.FundListComponent),
          },
          {
            path: 'categories',
            loadComponent: () =>
              import('./pages/account-list/account-list.component').then(m => m.AccountListComponent),
          },
        ],
      },
      {
        path: 'vendors',
        loadComponent: () =>
          import('./pages/vendor-list/vendor-list.component').then(m => m.VendorListComponent),
      },
      {
        path: 'service-requests',
        loadComponent: () =>
          import('./pages/service-request-list/service-request-list.component').then(
            m => m.ServiceRequestListComponent,
          ),
      },
      {
        path: 'stripe',
        loadComponent: () =>
          import('./pages/stripe-shell/stripe-shell.component').then(m => m.StripeShellComponent),
        children: [
          { path: '', redirectTo: 'payments', pathMatch: 'full' },
          {
            path: 'payments',
            loadComponent: () =>
              import('./pages/stripe-events-list/stripe-events-list.component').then(
                m => m.StripeEventsListComponent,
              ),
          },
          {
            path: 'mappings',
            loadComponent: () =>
              import('./pages/stripe-mapping-list/stripe-mapping-list.component').then(
                m => m.StripeMappingListComponent,
              ),
          },
        ],
      },
      {
        path: 'zeffy',
        loadComponent: () =>
          import('./pages/zeffy-shell/zeffy-shell.component').then(m => m.ZeffyShellComponent),
        children: [
          { path: '', redirectTo: 'campaigns', pathMatch: 'full' },
          {
            path: 'campaigns',
            loadComponent: () =>
              import('./pages/zeffy-campaign-list/zeffy-campaign-list.component').then(
                m => m.ZeffyCampaignListComponent,
              ),
          },
          {
            path: 'webhook-events',
            loadComponent: () =>
              import('./pages/zeffy-webhook-event-list/zeffy-webhook-event-list.component').then(
                m => m.ZeffyWebhookEventListComponent,
              ),
          },
        ],
      },
      {
        path: 'reports',
        loadComponent: () =>
          import('./pages/reports-shell/reports-shell.component').then(m => m.ReportsShellComponent),
        children: [
          { path: '', redirectTo: 'activities', pathMatch: 'full' },
          {
            path: 'activities',
            loadComponent: () =>
              import('./pages/statement-of-activities/statement-of-activities.component').then(
                m => m.StatementOfActivitiesComponent,
              ),
          },
          {
            path: 'financial-position',
            loadComponent: () =>
              import(
                './pages/statement-of-financial-position/statement-of-financial-position.component'
              ).then(m => m.StatementOfFinancialPositionComponent),
          },
          {
            path: 'funds-overview',
            loadComponent: () =>
              import('./pages/funds-overview/funds-overview.component').then(
                m => m.FundsOverviewComponent,
              ),
          },
        ],
      },
    ],
  },
];
