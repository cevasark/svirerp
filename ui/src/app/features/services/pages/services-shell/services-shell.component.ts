import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'app-services-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatTabsModule],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="tabPanel" class="services-tabs">
      <a mat-tab-link routerLink="room-rentals" routerLinkActive #roomRentalsActive="routerLinkActive"
         [active]="roomRentalsActive.isActive">
        Room Rentals
      </a>
      <a mat-tab-link routerLink="ceremonies" routerLinkActive #ceremoniesActive="routerLinkActive"
         [active]="ceremoniesActive.isActive">
        Church Ceremonies
      </a>
    </nav>
    <mat-tab-nav-panel #tabPanel>
      <router-outlet />
    </mat-tab-nav-panel>
  `,
  styles: [`
    .services-tabs { margin-bottom: 8px; }
  `],
})
export class ServicesShellComponent {}
