import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

interface ChecklistItemActionDialogData {
  action: 'done' | 'skipped';
  itemText: string;
}

/** Optional one-line detail captured at the moment a checklist item is marked Done or Skipped
 *  (e.g. why it was skipped) — a tiny modal, same "modal entry" pattern as
 *  ProjectChecklistFormComponent, rather than an inline field on the checklist card. Closes with
 *  the entered detail (possibly `''`) on confirm, or `undefined` on Cancel — callers use that
 *  distinction to tell "confirmed with no detail" apart from "cancelled the action entirely". */
@Component({
  selector: 'app-checklist-item-action-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Mark "{{ data.itemText }}" as {{ data.action === 'done' ? 'Done' : 'Skipped' }}</h2>

    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Detail (optional)</mat-label>
        <input matInput [(ngModel)]="detail" maxlength="500" />
      </mat-form-field>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" (click)="confirm()">
        Mark {{ data.action === 'done' ? 'Done' : 'Skipped' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
  `],
})
export class ChecklistItemActionFormComponent {
  private dialogRef = inject(MatDialogRef<ChecklistItemActionFormComponent>);
  data = inject<ChecklistItemActionDialogData>(MAT_DIALOG_DATA);

  detail = '';

  confirm(): void {
    this.dialogRef.close(this.detail.trim());
  }
}
