import { Component, computed, inject, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';

import { NavComponent } from '../nav/nav.component';
import { AuthService } from '../../core/services/auth.service';
import { NavCollapseService } from '../../core/services/nav-collapse.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    MatSidenavModule,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    NavComponent,
  ],
  template: `
    <mat-sidenav-container class="shell-container" #container>

      <mat-sidenav
        #sidenav
        [mode]="isMobile() ? 'over' : 'side'"
        [opened]="!isMobile()"
        class="shell-sidenav"
        [class.collapsed]="navCollapsed()"
        (transitionend)="container.updateContentMargins()">

        <div class="brand-header">
          @if (!navCollapsed()) {
            <img class="brand-icon" src="assets/logo.png" alt="SvirERP logo">
            <span class="brand-name">SvirERP</span>
            <span class="brand-header-spacer"></span>
          }
          @if (!isMobile()) {
            <button mat-icon-button (click)="navCollapse.toggle()"
                    [matTooltip]="navCollapsed() ? 'Expand navigation' : 'Collapse navigation'"
                    [attr.aria-label]="navCollapsed() ? 'Expand navigation' : 'Collapse navigation'">
              <mat-icon>{{ navCollapsed() ? 'chevron_right' : 'chevron_left' }}</mat-icon>
            </button>
          }
        </div>

        <app-nav [collapsed]="navCollapsed()" (navigate)="isMobile() && sidenav.close()" />
      </mat-sidenav>

      <mat-sidenav-content class="shell-content">
        <mat-toolbar color="primary" class="shell-toolbar">
          <button mat-icon-button (click)="sidenav.toggle()" aria-label="Toggle navigation">
            <mat-icon>menu</mat-icon>
          </button>
          <span class="toolbar-app-name">SvirERP</span>
          <span class="toolbar-spacer"></span>
          @if (auth.currentUser(); as user) {
            <span class="toolbar-user">{{ user.name || user.email }}</span>
          }
          <button mat-icon-button (click)="logout()" aria-label="Log out" matTooltip="Log out">
            <mat-icon>logout</mat-icon>
          </button>
        </mat-toolbar>

        <main class="shell-main">
          <router-outlet />
        </main>
      </mat-sidenav-content>

    </mat-sidenav-container>
  `,
  styles: [`
    .shell-container { height: 100vh; }
    .shell-sidenav { width: 240px; border-right: 1px solid rgba(0,0,0,.12); transition: width .15s ease; }
    .shell-sidenav.collapsed { width: 64px; }
    .brand-header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 16px;
      border-bottom: 1px solid rgba(0,0,0,.08);
    }
    .shell-sidenav.collapsed .brand-header { padding: 8px; justify-content: center; }
    .brand-icon { width: 32px; height: 32px; object-fit: contain; flex-shrink: 0; }
    .brand-name { font-size: 20px; font-weight: 500; white-space: nowrap; }
    .brand-header-spacer { flex: 1 1 auto; }
    .shell-toolbar { position: sticky; top: 0; z-index: 10; }
    .toolbar-app-name { margin-left: 8px; font-size: 18px; }
    .toolbar-spacer { flex: 1 1 auto; }
    .toolbar-user { margin-right: 8px; font-size: 14px; opacity: .85; }
    .shell-main { padding: 0; min-height: calc(100vh - 64px); }
  `],
})
// mat-sidenav-container only recalculates its content's left margin ("push" layout for
// mode="side") on drawer open/close, mode changes, or viewport resize — not on an arbitrary CSS
// width change to an already-open drawer like our collapse toggle. Listening for the sidenav's own
// width `transitionend` and calling `updateContentMargins()` then (see template) is the documented
// way to force a re-measure once the new width has actually finished rendering.
export class ShellComponent {
  private breakpoint = inject(BreakpointObserver);
  private router = inject(Router);
  protected auth = inject(AuthService);
  protected navCollapse = inject(NavCollapseService);

  isMobile = toSignal(
    this.breakpoint.observe(Breakpoints.Handset).pipe(map(r => r.matches)),
    { initialValue: false },
  );

  // Icon-only rail only applies in desktop "side" mode — mobile's "over" mode is a temporary
  // overlay where full labels stay more usable, regardless of the remembered desktop preference.
  navCollapsed = computed(() => !this.isMobile() && this.navCollapse.collapsed());

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
