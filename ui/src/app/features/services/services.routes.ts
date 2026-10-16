import { Routes } from '@angular/router';

export const servicesRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/services-shell/services-shell.component').then(
        m => m.ServicesShellComponent,
      ),
    children: [
      { path: '', redirectTo: 'room-rentals', pathMatch: 'full' },
      {
        path: 'room-rentals',
        loadComponent: () =>
          import('./pages/room-rentals/room-rentals.component').then(
            m => m.RoomRentalsComponent,
          ),
      },
      {
        path: 'ceremonies',
        loadComponent: () =>
          import('../finance/pages/service-request-list/service-request-list.component').then(
            m => m.ServiceRequestListComponent,
          ),
      },
    ],
  },
];
