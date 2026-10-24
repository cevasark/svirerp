import { Component, OnInit, inject, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { UserSettingsService } from '../../services/user-settings.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { AppUserSummary } from '../../../../core/models/api.model';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-user-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    DatePipe,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    PageHeaderComponent,
  ],
  template: `
    <div class="page-container">
      <app-page-header
        title="Users"
        subtitle="Tracked user accounts and login activity">
        <div extraActions class="header-actions">
          <button mat-stroked-button (click)="load()" [disabled]="loading()">
            <mat-icon>refresh</mat-icon>
            Refresh
          </button>
        </div>
      </app-page-header>

      <!-- Overview Stats Cards -->
      <div class="stats-grid">
        <mat-card class="stat-card">
          <mat-card-content>
            <div class="stat-icon-wrapper users">
              <mat-icon>people</mat-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ stats().totalUsers }}</div>
              <div class="stat-label">Total Users</div>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-content>
            <div class="stat-icon-wrapper google">
              <mat-icon>account_circle</mat-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ stats().googleUsers }}</div>
              <div class="stat-label">Google OAuth Users</div>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-content>
            <div class="stat-icon-wrapper local">
              <mat-icon>admin_panel_settings</mat-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ stats().localUsers }}</div>
              <div class="stat-label">Local Admin Accounts</div>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-content>
            <div class="stat-icon-wrapper recent">
              <mat-icon>schedule</mat-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value small">
                {{ stats().latestLogin ? timeAgo(stats().latestLogin?.lastLoginAt ?? null) : 'Never' }}
              </div>
              <div class="stat-label">
                {{ stats().latestLogin ? stats().latestLogin?.email : 'No logins yet' }}
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      </div>

      <!-- Controls & Table Card -->
      <mat-card class="content-card">
        @if (loading()) {
          <mat-progress-bar mode="indeterminate" />
        }

        <mat-card-content>
          <!-- Toolbar -->
          <div class="table-toolbar">
            <mat-form-field appearance="outline" class="search-field" subscriptSizing="dynamic">
              <mat-icon matPrefix>search</mat-icon>
              <mat-label>Search by email or name</mat-label>
              <input
                matInput
                [ngModel]="searchTerm()"
                (ngModelChange)="searchTerm.set($event)"
                placeholder="Filter users..." />
              @if (searchTerm()) {
                <button mat-icon-button matSuffix (click)="searchTerm.set('')" aria-label="Clear">
                  <mat-icon>close</mat-icon>
                </button>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="provider-filter" subscriptSizing="dynamic">
              <mat-label>Provider</mat-label>
              <mat-select [ngModel]="providerFilter()" (ngModelChange)="providerFilter.set($event)">
                <mat-option value="all">All Providers</mat-option>
                <mat-option value="google">Google</mat-option>
                <mat-option value="local">Local Admin</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <!-- Table -->
          <div class="table-responsive">
            <table mat-table [dataSource]="filteredUsers()" class="users-table">
              <!-- User / Email Column -->
              <ng-container matColumnDef="user">
                <th mat-header-cell *matHeaderCellDef>User</th>
                <td mat-cell *matCellDef="let user">
                  <div class="user-cell">
                    <div class="avatar" [attr.data-provider]="getNormalizedProvider(user.provider)">
                      {{ getInitial(user) }}
                    </div>
                    <div class="user-details">
                      <div class="user-email">{{ user.email }}</div>
                      @if (user.name) {
                        <div class="user-name">{{ user.name }}</div>
                      }
                    </div>
                  </div>
                </td>
              </ng-container>

              <!-- Provider Column -->
              <ng-container matColumnDef="provider">
                <th mat-header-cell *matHeaderCellDef>Provider</th>
                <td mat-cell *matCellDef="let user">
                  <span class="provider-badge" [attr.data-provider]="getNormalizedProvider(user.provider)">
                    <mat-icon class="badge-icon">
                      {{ getNormalizedProvider(user.provider) === 'google' ? 'account_circle' : 'admin_panel_settings' }}
                    </mat-icon>
                    {{ formatProvider(user.provider) }}
                  </span>
                </td>
              </ng-container>

              <!-- Last Login Date & Time Column -->
              <ng-container matColumnDef="lastLogin">
                <th mat-header-cell *matHeaderCellDef>Last Login</th>
                <td mat-cell *matCellDef="let user">
                  @if (user.lastLoginAt) {
                    <div class="login-time-cell">
                      <span class="login-date-main">{{ user.lastLoginAt | date:'medium' }}</span>
                      <span class="relative-badge" [matTooltip]="user.lastLoginAt | date:'full'">
                        {{ timeAgo(user.lastLoginAt) }}
                      </span>
                    </div>
                  } @else {
                    <span class="text-muted">Never logged in</span>
                  }
                </td>
              </ng-container>

              <!-- Login Count Column -->
              <ng-container matColumnDef="loginCount">
                <th mat-header-cell *matHeaderCellDef class="text-center">Logins</th>
                <td mat-cell *matCellDef="let user" class="text-center">
                  <span class="count-badge" [matTooltip]="(user.loginCount || 1) + ' total login session(s)'">
                    {{ user.loginCount || 1 }}
                  </span>
                </td>
              </ng-container>

              <!-- First Seen / Created At Column -->
              <ng-container matColumnDef="createdAt">
                <th mat-header-cell *matHeaderCellDef>First Recorded</th>
                <td mat-cell *matCellDef="let user">
                  <span class="text-secondary">{{ user.createdAt | date:'mediumDate' }}</span>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>

              <!-- Row shown when there is no matching data -->
              <tr class="mat-row" *matNoDataRow>
                <td class="mat-cell empty-cell" [attr.colspan]="displayedColumns.length">
                  @if (loading()) {
                    <div class="empty-state">Loading users...</div>
                  } @else if (searchTerm() || providerFilter() !== 'all') {
                    <div class="empty-state">
                      <mat-icon class="empty-icon">search_off</mat-icon>
                      <p class="empty-title">No matching users</p>
                      <p class="empty-subtitle">Try adjusting your search query or provider filter.</p>
                    </div>
                  } @else {
                    <div class="empty-state">
                      <mat-icon class="empty-icon">person_outline</mat-icon>
                      <p class="empty-title">No user logins recorded yet</p>
                      <p class="empty-subtitle">When users sign in with Google or local admin, their logins will appear here.</p>
                    </div>
                  }
                </td>
              </tr>
            </table>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container {
      padding: 0 0 24px;
    }

    .header-actions {
      display: flex;
      gap: 8px;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      margin-bottom: 20px;
    }

    .stat-card mat-card-content {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
    }

    .stat-icon-wrapper {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .stat-icon-wrapper.users { background: #e0f2fe; color: #0284c7; }
    .stat-icon-wrapper.google { background: #ecfdf5; color: #059669; }
    .stat-icon-wrapper.local { background: #fef3c7; color: #d97706; }
    .stat-icon-wrapper.recent { background: #f3e8ff; color: #7c3aed; }

    .stat-value {
      font-size: 1.5rem;
      font-weight: 700;
      line-height: 1.2;
    }

    .stat-value.small {
      font-size: 1.1rem;
      font-weight: 600;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 170px;
    }

    .stat-label {
      font-size: 0.85rem;
      color: rgba(0, 0, 0, 0.54);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 170px;
    }

    .content-card {
      overflow: hidden;
    }

    .table-toolbar {
      display: flex;
      gap: 16px;
      align-items: center;
      padding: 16px 16px 8px;
      flex-wrap: wrap;
    }

    .search-field {
      flex: 1;
      min-width: 240px;
    }

    .provider-filter {
      width: 180px;
    }

    .table-responsive {
      overflow-x: auto;
    }

    .users-table {
      width: 100%;
    }

    .user-cell {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 0;
    }

    .avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: #e2e8f0;
      color: #334155;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 600;
      font-size: 0.95rem;
      flex-shrink: 0;
    }

    .avatar[data-provider="google"] {
      background: #e0f2fe;
      color: #0369a1;
    }

    .avatar[data-provider="local"] {
      background: #fef3c7;
      color: #b45309;
    }

    .user-details {
      display: flex;
      flex-direction: column;
    }

    .user-email {
      font-weight: 600;
      color: #1e293b;
    }

    .user-name {
      font-size: 0.8rem;
      color: rgba(0, 0, 0, 0.54);
    }

    .provider-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 16px;
      font-size: 0.78rem;
      font-weight: 600;
      letter-spacing: 0.02em;
    }

    .badge-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }

    .provider-badge[data-provider="google"] {
      background: #eff6ff;
      color: #1d4ed8;
      border: 1px solid #bfdbfe;
    }

    .provider-badge[data-provider="local"] {
      background: #fffbeb;
      color: #b45309;
      border: 1px solid #fde68a;
    }

    .login-time-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .login-date-main {
      font-weight: 500;
      color: #1e293b;
    }

    .relative-badge {
      font-size: 0.75rem;
      color: #64748b;
    }

    .count-badge {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 12px;
      background: #f1f5f9;
      color: #334155;
      font-weight: 600;
      font-size: 0.82rem;
    }

    .text-center {
      text-align: center;
    }

    .text-secondary {
      color: #64748b;
      font-size: 0.85rem;
    }

    .text-muted {
      color: rgba(0, 0, 0, 0.38);
      font-style: italic;
      font-size: 0.85rem;
    }

    .empty-cell {
      padding: 48px 16px !important;
      text-align: center;
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
    }

    .empty-icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: #94a3b8;
    }

    .empty-title {
      font-size: 1rem;
      font-weight: 600;
      margin: 0;
      color: #475569;
    }

    .empty-subtitle {
      font-size: 0.85rem;
      margin: 0;
      color: #94a3b8;
    }
  `],
})
export class UserSettingsComponent implements OnInit {
  private readonly userSettingsService = inject(UserSettingsService);
  private readonly notifications = inject(NotificationService);

  readonly displayedColumns = ['user', 'provider', 'lastLogin', 'loginCount', 'createdAt'];

  readonly users = signal<AppUserSummary[]>([]);
  readonly loading = signal(false);
  readonly searchTerm = signal('');
  readonly providerFilter = signal<'all' | 'google' | 'local'>('all');

  readonly filteredUsers = computed(() => {
    const list = this.users();
    const query = this.searchTerm().trim().toLowerCase();
    const provider = this.providerFilter();

    return list.filter(u => {
      // Provider filter
      if (provider !== 'all') {
        const norm = this.getNormalizedProvider(u.provider);
        if (norm !== provider) return false;
      }

      // Search query filter
      if (query) {
        const emailMatch = u.email?.toLowerCase().includes(query);
        const nameMatch = u.name?.toLowerCase().includes(query);
        return emailMatch || nameMatch;
      }

      return true;
    });
  });

  readonly stats = computed(() => {
    const list = this.users();
    const totalUsers = list.length;
    let googleUsers = 0;
    let localUsers = 0;
    let latestLogin: AppUserSummary | null = null;

    for (const u of list) {
      const norm = this.getNormalizedProvider(u.provider);
      if (norm === 'google') googleUsers++;
      if (norm === 'local') localUsers++;

      if (u.lastLoginAt) {
        if (!latestLogin || !latestLogin.lastLoginAt || new Date(u.lastLoginAt) > new Date(latestLogin.lastLoginAt)) {
          latestLogin = u;
        }
      }
    }

    return { totalUsers, googleUsers, localUsers, latestLogin };
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.userSettingsService.listUsers().subscribe({
      next: data => {
        this.users.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notifications.error('Failed to load users list.');
      },
    });
  }

  getNormalizedProvider(provider: string | null | undefined): 'google' | 'local' | 'other' {
    if (!provider) return 'other';
    const lower = provider.toLowerCase();
    if (lower.includes('google')) return 'google';
    if (lower.includes('local')) return 'local';
    return 'other';
  }

  formatProvider(provider: string | null | undefined): string {
    const norm = this.getNormalizedProvider(provider);
    if (norm === 'google') return 'Google';
    if (norm === 'local') return 'Local Admin';
    return provider || 'Unknown';
  }

  getInitial(user: AppUserSummary): string {
    if (user.name && user.name.trim().length > 0) {
      return user.name.trim().charAt(0).toUpperCase();
    }
    if (user.email && user.email.trim().length > 0) {
      return user.email.trim().charAt(0).toUpperCase();
    }
    return '?';
  }

  timeAgo(dateStr: string | null): string {
    if (!dateStr) return 'Never';
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    if (isNaN(diffMs) || diffMs < 0) return 'Just now';

    const diffSecs = Math.floor(diffMs / 1000);
    const diffMins = Math.floor(diffSecs / 60);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffSecs < 60) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 30) return `${diffDays}d ago`;

    const diffMonths = Math.floor(diffDays / 30);
    if (diffMonths < 12) return `${diffMonths}mo ago`;

    const diffYears = Math.floor(diffDays / 365);
    return `${diffYears}y ago`;
  }
}
