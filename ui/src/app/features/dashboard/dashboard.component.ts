import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

interface QuickLink {
  label: string;
  icon: string;
  route: string;
  description: string;
  color: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatCardModule, MatIconModule],
  template: `
    <div class="page-container">
      <h1 class="mat-headline-5">Welcome to SvirERP</h1>
      <p class="mat-body-1 welcome-sub">
        Non-profit ERP system — select a module below to get started.
      </p>

      <div class="module-grid">
        @for (link of modules; track link.route) {
          <mat-card [routerLink]="link.route" class="module-card mat-elevation-z2">
            <mat-card-header>
              <div mat-card-avatar class="module-avatar" [style.background]="link.color">
                <mat-icon class="module-icon">{{ link.icon }}</mat-icon>
              </div>
              <mat-card-title>{{ link.label }}</mat-card-title>
              <mat-card-subtitle>{{ link.description }}</mat-card-subtitle>
            </mat-card-header>
          </mat-card>
        }
      </div>
    </div>
  `,
  styles: [`
    .welcome-sub { color: rgba(0,0,0,.54); margin-bottom: 32px; }

    .module-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 16px;
    }

    .module-card {
      cursor: pointer;
      transition: box-shadow .2s, transform .2s;
    }
    .module-card:hover { box-shadow: 0 6px 16px rgba(0,0,0,.18); transform: translateY(-2px); }

    .module-avatar {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .module-icon { color: white; }
  `],
})
export class DashboardComponent {
  readonly modules: QuickLink[] = [
    { label: 'People',        icon: 'people',             route: '/persons',       description: 'Contacts and individuals',              color: '#3f51b5' },
    { label: 'Organizations', icon: 'business',           route: '/organizations', description: 'Church and org profiles',               color: '#0097a7' },
    { label: 'Membership',    icon: 'card_membership',    route: '/membership',    description: 'Members, types and dues',               color: '#388e3c' },
    { label: 'Governance',    icon: 'gavel',              route: '/governance',    description: 'Trustees, committees and resolutions',  color: '#f57c00' },
    { label: 'Volunteers',    icon: 'volunteer_activism', route: '/volunteers',    description: 'Volunteer hours and assignments',       color: '#7b1fa2' },
    { label: 'Events',        icon: 'event',              route: '/events',        description: 'Calendar and registrations',            color: '#c2185b' },
    { label: 'Finance',       icon: 'account_balance',    route: '/finance',       description: 'Accounts, journal entries, bank recon', color: '#5d4037' },
  ];
}
