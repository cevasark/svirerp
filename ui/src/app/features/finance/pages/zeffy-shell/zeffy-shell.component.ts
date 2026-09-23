import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'app-zeffy-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatTabsModule],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="tabPanel" class="zeffy-tabs">
      <a mat-tab-link routerLink="campaigns" routerLinkActive #campaignsActive="routerLinkActive"
         [active]="campaignsActive.isActive">Campaigns</a>
      <a mat-tab-link routerLink="webhook-events" routerLinkActive #eventsActive="routerLinkActive"
         [active]="eventsActive.isActive">Webhook Events</a>
    </nav>
    <mat-tab-nav-panel #tabPanel>
      <router-outlet />
    </mat-tab-nav-panel>
  `,
  styles: [`
    .zeffy-tabs { margin-bottom: 8px; }
  `],
})
export class ZeffyShellComponent {}
