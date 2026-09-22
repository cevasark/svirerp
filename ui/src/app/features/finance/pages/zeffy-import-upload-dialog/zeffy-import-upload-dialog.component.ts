import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ZeffyImportService } from '../../services/zeffy-import.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ZeffyImportBatch } from '../../../../core/models/domain.model';

interface ZeffyImportUploadDialogData {
  orgId: string;
}

/** Uploads a Zeffy CSV and hands the resulting batch back to the caller, which navigates to the
 * detail page — the substantive preview/mapping/commit flow lives there, not in this dialog. */
@Component({
  selector: 'app-zeffy-import-upload-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Import Zeffy Transactions</h2>

    <mat-dialog-content class="import-dialog">
      <p>
        Upload a Zeffy <strong>Transactions</strong> export (.xlsx or .csv — not the older Payments
        export, which is no longer accepted). You'll get a preview of every row — new
        people/members, duplicates, and any campaigns that still need a fund assigned — before
        anything is actually recorded.
      </p>

      <input type="file" accept=".xlsx,.xls,.csv" (change)="onFileSelected($event)" />

      @if (fileName()) {
        <p class="selected-file">Selected: {{ fileName() }}</p>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="close()">Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!selectedFile() || uploading()" (click)="upload()">
        @if (uploading()) {
          <mat-progress-spinner diameter="20" mode="indeterminate" />
        } @else {
          Upload &amp; Preview
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .import-dialog { min-width: 480px; }
    .selected-file { color: rgba(0,0,0,.6); }
  `],
})
export class ZeffyImportUploadDialogComponent {
  private zeffyImportService = inject(ZeffyImportService);
  private dialogRef = inject(MatDialogRef<ZeffyImportUploadDialogComponent>);
  private notifications = inject(NotificationService);
  private data = inject<ZeffyImportUploadDialogData>(MAT_DIALOG_DATA);

  selectedFile = signal<File | null>(null);
  fileName = signal<string | null>(null);
  uploading = signal(false);

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
    this.zeffyImportService.preview(this.data.orgId, file).subscribe({
      next: (batch: ZeffyImportBatch) => {
        this.uploading.set(false);
        this.dialogRef.close(batch);
      },
      error: () => {
        this.uploading.set(false);
        this.notifications.error('Upload failed — check the file and try again.');
      },
    });
  }

  close(): void {
    this.dialogRef.close(null);
  }
}
