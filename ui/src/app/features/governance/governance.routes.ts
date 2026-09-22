import { Routes } from '@angular/router';

export const governanceRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/governance-shell/governance-shell.component').then(
        m => m.GovernanceShellComponent,
      ),
    children: [
      { path: '', redirectTo: 'projects', pathMatch: 'full' },
      {
        path: 'trustees',
        loadComponent: () =>
          import('./pages/trustee-list/trustee-list.component').then(
            m => m.TrusteeListComponent,
          ),
      },
      {
        path: 'meeting-minutes',
        loadComponent: () =>
          import('./pages/meeting-minutes-list/meeting-minutes-list.component').then(
            m => m.MeetingMinutesListComponent,
          ),
      },
      {
        path: 'meeting-minutes/:id',
        loadComponent: () =>
          import('./pages/meeting-minutes-detail/meeting-minutes-detail.component').then(
            m => m.MeetingMinutesDetailComponent,
          ),
      },
      {
        path: 'projects',
        loadComponent: () =>
          import('./pages/project-list/project-list.component').then(
            m => m.ProjectListComponent,
          ),
      },
      {
        path: 'projects/:id',
        loadComponent: () =>
          import('./pages/project-detail/project-detail.component').then(
            m => m.ProjectDetailComponent,
          ),
      },
    ],
  },
];
