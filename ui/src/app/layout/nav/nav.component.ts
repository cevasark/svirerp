import { Component, computed, inject, input, output, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../core/services/auth.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-nav',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, MatListModule, MatIconModule, MatTooltipModule],
  template: `
    <mat-nav-list class="nav-list">
      @for (item of navItems(); track item.route) {
        <a mat-list-item
           [routerLink]="item.route"
           routerLinkActive="nav-active"
           [matTooltip]="item.label"
           [matTooltipDisabled]="!collapsed()"
           matTooltipPosition="right"
           (click)="navigate.emit()">
          <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
          @if (!collapsed()) {
            <span matListItemTitle>{{ item.label }}</span>
          }
        </a>
      }
    </mat-nav-list>
  `,
  styles: [`
    .nav-list { padding-top: 8px; }
    .nav-active { background: rgba(63, 81, 181, .1) !important; border-radius: 4px; }
    .nav-active mat-icon { color: #3f51b5; }
  `],
})
export class NavComponent {
  private auth = inject(AuthService);

  navigate = output<void>();
  collapsed = input<boolean>(false);

  private readonly baseNavItems: NavItem[] = [
    { label: 'Dashboard',      icon: 'dashboard',           route: '/dashboard' },
    { label: 'People',         icon: 'people',              route: '/persons' },
    { label: 'Membership',     icon: 'card_membership',     route: '/membership' },
    { label: 'Governance',     icon: 'gavel',               route: '/governance' },
    { label: 'Volunteers',     icon: 'volunteer_activism',  route: '/volunteers' },
    { label: 'Events',         icon: 'event',               route: '/events' },
    { label: 'Finance',        icon: 'account_balance',     route: '/finance' },
  ];

  // Settings is admin-only — never shown to Google-authenticated users, even
  // though both pass authGuard (see core/guards/admin.guard.ts).
  readonly navItems = computed<NavItem[]>(() =>
    this.auth.currentUser()?.provider === 'local-admin'
      ? [...this.baseNavItems, { label: 'Settings', icon: 'settings', route: '/settings' }]
      : this.baseNavItems,
  );
}
