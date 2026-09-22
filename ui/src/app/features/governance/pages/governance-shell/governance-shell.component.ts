import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'app-governance-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatTabsModule],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="tabPanel" class="governance-tabs">
      <a mat-tab-link routerLink="projects" routerLinkActive #projectsActive="routerLinkActive"
         [active]="projectsActive.isActive">
        Projects
      </a>
      <a mat-tab-link routerLink="meeting-minutes" routerLinkActive #meetingMinutesActive="routerLinkActive"
         [active]="meetingMinutesActive.isActive">
        Meeting Minutes
      </a>
      <a mat-tab-link routerLink="trustees" routerLinkActive #trusteesActive="routerLinkActive"
         [active]="trusteesActive.isActive">
        Trustees
      </a>
    </nav>
    <mat-tab-nav-panel #tabPanel>
      <router-outlet />
    </mat-tab-nav-panel>
  `,
  styles: [`
    .governance-tabs { margin-bottom: 8px; }
  `],
})
export class GovernanceShellComponent {}
