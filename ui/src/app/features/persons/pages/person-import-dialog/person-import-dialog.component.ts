import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { PersonService } from '../../services/person.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { PersonImportResult } from '../../../../core/models/api.model';

interface PersonImportDialogData {
  orgId: string;
}

@Component({
  selector: 'app-person-import-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Import People</h2>

    <mat-dialog-content class="import-dialog">
      @if (!result()) {
        <p>
          Upload a Zeffy contacts export (.xlsx or .csv) — this catches people who registered for
          free ($0) membership, since a $0 registration never produces a transaction and so never
          shows up in the Zeffy Transactions import. Anyone whose email already exists in the
          system is left untouched; everyone else is added as a Follower (inactive if their
          Unsubscribed column is Yes/true, active otherwise). One bad row won't block the rest —
          you'll get a report of exactly which rows failed and why.
        </p>

        <input type="file" accept=".xlsx,.xls,.csv" (change)="onFileSelected($event)" />

        @if (fileName()) {
          <p class="selected-file">Selected: {{ fileName() }}</p>
        }
      } @else {
        <p class="summary">
          <strong>{{ result()!.created }}</strong> added as Followers,
          <strong>{{ result()!.skippedExisting }}</strong> already existed and were skipped.
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
          <p>No failures — every row processed successfully.</p>
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
export class PersonImportDialogComponent {
  private personService = inject(PersonService);
  private dialogRef = inject(MatDialogRef<PersonImportDialogComponent>);
  private notifications = inject(NotificationService);
  private data = inject<PersonImportDialogData>(MAT_DIALOG_DATA);

  selectedFile = signal<File | null>(null);
  fileName = signal<string | null>(null);
  uploading = signal(false);
  result = signal<PersonImportResult | null>(null);
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
    this.personService.importPeople(this.data.orgId, file).subscribe({
      next: res => {
        this.uploading.set(false);
        this.result.set(res);
        this.importedAnything = res.created > 0;
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
