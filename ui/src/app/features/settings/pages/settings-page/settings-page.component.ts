import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { SettingsService } from '../../services/settings.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { AppSetting } from '../../../../core/models/api.model';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';

interface SettingRow {
  setting: AppSetting;
  draftValue: string;
  saving: boolean;
}

@Component({
  selector: 'app-settings-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    PageHeaderComponent,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-container">
      <app-page-header
        title="Settings"
        subtitle="Admin-only runtime configuration" />

      @for (row of rows(); track row.setting.key) {
        <mat-card class="setting-card">
          <mat-card-content>
            <div class="setting-row">
              <div class="setting-info">
                <div class="setting-label">{{ row.setting.description || row.setting.key }}</div>
                @if (row.setting.valueType === 'SECRET') {
                  <div class="setting-status">
                    {{ row.setting.hasValue ? 'Configured — leave blank to keep the current value' : 'Not configured' }}
                  </div>
                }
              </div>

              @switch (row.setting.valueType) {
                @case ('BOOLEAN') {
                  <mat-checkbox [(ngModel)]="row.draftValue" [ngModelOptions]="{ standalone: true }">
                    Enabled
                  </mat-checkbox>
                }
                @case ('SECRET') {
                  <mat-form-field appearance="outline" class="setting-input">
                    <input matInput type="password" [(ngModel)]="row.draftValue"
                           [placeholder]="row.setting.hasValue ? '••••••••' : 'Not set'"
                           autocomplete="new-password" />
                  </mat-form-field>
                }
                @case ('NUMBER') {
                  <mat-form-field appearance="outline" class="setting-input">
                    <input matInput type="number" [(ngModel)]="row.draftValue" />
                  </mat-form-field>
                }
                @default {
                  <mat-form-field appearance="outline" class="setting-input">
                    <input matInput type="text" [(ngModel)]="row.draftValue" />
                  </mat-form-field>
                }
              }

              <button mat-flat-button color="primary" [disabled]="row.saving" (click)="save(row)">
                @if (row.saving) {
                  <mat-progress-spinner diameter="20" mode="indeterminate" />
                } @else {
                  Save
                }
              </button>
            </div>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: [`
    .setting-card { margin-bottom: 12px; }
    .setting-row { display: flex; align-items: center; gap: 16px; }
    .setting-info { flex: 1; }
    .setting-label { font-weight: 500; }
    .setting-status { font-size: 0.85em; color: rgba(0,0,0,.6); }
    .setting-input { width: 320px; margin-bottom: -1.25em; }
  `],
})
export class SettingsPageComponent implements OnInit {
  private settingsService = inject(SettingsService);
  private notifications = inject(NotificationService);

  rows = signal<SettingRow[]>([]);

  ngOnInit(): void {
    this.load();
  }

  save(row: SettingRow): void {
    row.saving = true;
    this.rows.set([...this.rows()]);
    this.settingsService.update(row.setting.key, row.draftValue).subscribe({
      next: updated => {
        row.setting = updated;
        row.draftValue = row.setting.valueType === 'SECRET' ? '' : (updated.value ?? '');
        row.saving = false;
        this.rows.set([...this.rows()]);
        this.notifications.success(`${updated.description || updated.key} saved.`);
      },
      error: () => {
        row.saving = false;
        this.rows.set([...this.rows()]);
      },
    });
  }

  private load(): void {
    this.settingsService.list().subscribe(settings => {
      this.rows.set(settings.filter(setting => !setting.key.startsWith('gmail.')
        && !setting.key.startsWith('calendar.')
        && !setting.key.startsWith('email.')
        && !setting.key.startsWith('zeffy.')).map(setting => ({
        setting,
        draftValue: setting.valueType === 'SECRET' ? '' : (setting.value ?? ''),
        saving: false,
      })));
    });
  }
}
