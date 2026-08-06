import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-pagination',
  standalone: true,
  imports: [CommonModule],
  template: `<nav class="pagination" *ngIf="total > pageSize"><button type="button" [disabled]="page <= 1" (click)="go(page - 1)">Previous</button><span>Page {{page}} of {{pages}}</span><button type="button" [disabled]="page >= pages" (click)="go(page + 1)">Next</button></nav>`,
  styles: [`.pagination{display:flex;justify-content:flex-end;align-items:center;gap:12px;padding:16px 0 0;color:#647087;font-size:12px}.pagination button{border:1px solid #dfe4f0;border-radius:8px;background:#fff;color:#52617c;padding:8px 11px;font-weight:700;cursor:pointer}.pagination button:disabled{opacity:.45;cursor:default}`],
})
export class Pagination {
  @Input() page = 1;
  @Input() total = 0;
  @Input() pageSize = 10;
  @Output() pageChange = new EventEmitter<number>();
  get pages() { return Math.max(1, Math.ceil(this.total / this.pageSize)); }
  go(page: number) { if (page >= 1 && page <= this.pages) this.pageChange.emit(page); }
}