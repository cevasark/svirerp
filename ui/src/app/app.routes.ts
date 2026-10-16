import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

export const appRoutes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'portal-access',
    loadComponent: () =>
      import('./features/auth/portal-access.component').then(m => m.PortalAccessComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'persons',
        loadChildren: () =>
          import('./features/persons/persons.routes').then(m => m.personsRoutes),
      },
      {
        path: 'membership',
        loadChildren: () =>
          import('./features/membership/membership.routes').then(m => m.membershipRoutes),
      },
      {
        path: 'governance',
        loadChildren: () =>
          import('./features/governance/governance.routes').then(m => m.governanceRoutes),
      },
      {
        path: 'volunteers',
        loadChildren: () =>
          import('./features/volunteers/volunteers.routes').then(m => m.volunteersRoutes),
      },
      {
        path: 'events',
        loadChildren: () =>
          import('./features/events/events.routes').then(m => m.eventsRoutes),
      },
      {
        path: 'finance',
        loadChildren: () =>
          import('./features/finance/finance.routes').then(m => m.financeRoutes),
      },
      {
        path: 'services',
        loadChildren: () =>
          import('./features/services/services.routes').then(m => m.servicesRoutes),
      },
      {
        path: 'settings',
        canActivate: [adminGuard],
        loadChildren: () =>
          import('./features/settings/settings.routes').then(m => m.settingsRoutes),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
