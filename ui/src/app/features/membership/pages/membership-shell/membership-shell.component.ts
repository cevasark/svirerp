import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'app-membership-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatTabsModule],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="tabPanel" class="membership-tabs">
      <a mat-tab-link routerLink="members" routerLinkActive #membersActive="routerLinkActive"
         [active]="membersActive.isActive">
        Members
      </a>
      <a mat-tab-link routerLink="contributions" routerLinkActive #contributionsActive="routerLinkActive"
         [active]="contributionsActive.isActive">
        Contributions
      </a>
      <a mat-tab-link routerLink="types" routerLinkActive #typesActive="routerLinkActive"
         [active]="typesActive.isActive">
        Membership Types
      </a>
    </nav>
    <mat-tab-nav-panel #tabPanel>
      <router-outlet />
    </mat-tab-nav-panel>
  `,
  styles: [`
    .membership-tabs { margin-bottom: 8px; }
  `],
})
export class MembershipShellComponent {}
