import { Routes } from '@angular/router';

export const settingsRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/settings-shell/settings-shell.component').then(
        m => m.SettingsShellComponent,
      ),
    children: [
      { path: '', redirectTo: 'general', pathMatch: 'full' },
      {
        path: 'general',
        loadComponent: () =>
          import('./pages/settings-page/settings-page.component').then(
            m => m.SettingsPageComponent,
          ),
      },
      {
        path: 'organization',
        loadComponent: () =>
          import('./pages/organization-settings/organization-settings.component').then(
            m => m.OrganizationSettingsComponent,
          ),
      },
      {
        path: 'gmail',
        loadComponent: () =>
          import('./pages/gmail-settings/gmail-settings.component').then(
            m => m.GmailSettingsComponent,
          ),
      },
      {
        path: 'calendar',
        loadComponent: () =>
          import('./pages/calendar-settings/calendar-settings.component').then(
            m => m.CalendarSettingsComponent,
          ),
      },
      {
        path: 'email',
        loadComponent: () =>
          import('./pages/email-settings/email-settings.component').then(
            m => m.EmailSettingsComponent,
          ),
      },
      {
        path: 'stripe',
        loadComponent: () =>
          import('./pages/stripe-settings/stripe-settings.component').then(
            m => m.StripeSettingsComponent,
          ),
      },
      {
        path: 'zeffy',
        loadComponent: () =>
          import('./pages/zeffy-settings/zeffy-settings.component').then(
            m => m.ZeffySettingsComponent,
          ),
      },
    ],
  },
];
