import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { MemberService } from '../../services/member.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { MemberImportResult } from '../../../../core/models/api.model';

@Component({
  selector: 'app-member-import-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Import Members</h2>

    <mat-dialog-content class="import-dialog">
      @if (!result()) {
        <p>
          Upload a filled-in copy of the member import template (CSV). Rows matching an
          existing person's email update that person's membership; new emails create a
          new person and membership. One bad row won't block the rest — you'll get a
          report of exactly which rows failed and why.
        </p>

        <input type="file" accept=".csv" (change)="onFileSelected($event)" />

        @if (fileName()) {
          <p class="selected-file">Selected: {{ fileName() }}</p>
        }
      } @else {
        <p class="summary">
          <strong>{{ result()!.created }}</strong> created, <strong>{{ result()!.updated }}</strong> updated.
        </p>

        @if (result()!.failed.length) {
          <p class="failures-heading">{{ result()!.failed.length }} row(s) failed:</p>
          <table class="failures-table">
            <thead>
              <tr><th>Row</th><th>Email</th><th>Reason</th></tr>
            </thead>
            <tbody>
              @for (row of result()!.failed; track row.rowNumber) {
                <tr>
                  <td>{{ row.rowNumber }}</td>
                  <td>{{ row.email ?? '—' }}</td>
                  <td>{{ row.message }}</td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <p>No failures — every row imported successfully.</p>
        }
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      @if (!result()) {
        <button mat-button (click)="close()">Cancel</button>
        <button mat-flat-button color="primary" [disabled]="!selectedFile() || uploading()" (click)="upload()">
          @if (uploading()) {
            <mat-progress-spinner diameter="20" mode="indeterminate" />
          } @else {
            Upload
          }
        </button>
      } @else {
        <button mat-flat-button color="primary" (click)="close()">Done</button>
      }
    </mat-dialog-actions>
  `,
  styles: [`
    .import-dialog { min-width: 480px; }
    .selected-file { color: rgba(0,0,0,.6); }
    .summary { font-size: 1.05em; }
    .failures-heading { font-weight: 500; margin-top: 12px; }
    .failures-table { width: 100%; border-collapse: collapse; font-size: 0.9em; }
    .failures-table th, .failures-table td {
      text-align: left; padding: 4px 8px; border-bottom: 1px solid rgba(0,0,0,.12);
    }
  `],
})
export class MemberImportDialogComponent {
  private memberService = inject(MemberService);
  private dialogRef = inject(MatDialogRef<MemberImportDialogComponent>);
  private notifications = inject(NotificationService);

  selectedFile = signal<File | null>(null);
  fileName = signal<string | null>(null);
  uploading = signal(false);
  result = signal<MemberImportResult | null>(null);
  private importedAnything = false;

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile.set(file);
    this.fileName.set(file?.name ?? null);
  }

  upload(): void {
    const file = this.selectedFile();
    if (!file) {
      return;
    }
    this.uploading.set(true);
    this.memberService.importMembers(file).subscribe({
      next: res => {
        this.uploading.set(false);
        this.result.set(res);
        this.importedAnything = res.created > 0 || res.updated > 0;
      },
      error: () => {
        this.uploading.set(false);
        this.notifications.error('Import failed — check the file and try again.');
      },
    });
  }

  close(): void {
    this.dialogRef.close(this.importedAnything);
  }
}
