import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

export interface CustomerOrder {
  bookingId: number;
  busNumber: number;
  bookingUser: string;
  source: string;
  destination: string;
  numberOfSeats: number;
  totalFare: number;
  status: 'CONFIRMED' | 'PENDING' | 'CANCELLED';
  bookingDate: string;
}

@Component({
  selector: 'app-orders',
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.scss',
  standalone: false
})
export class OrdersComponent implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);

  orders = signal<CustomerOrder[]>([]);
  isLoading = signal<boolean>(false);
  filterStatus: string = 'ALL';

  ngOnInit() {
    this.loadOrders();
  }

  loadOrders() {
    this.isLoading.set(true);
    this.http.get<any[]>('/bookingservice/v1/myBookings?username=admin').subscribe({
      next: (data) => {
        this.isLoading.set(false);
        if (data && data.length > 0) {
          const mapped: CustomerOrder[] = data.map(d => ({
            bookingId: d.bookingId,
            busNumber: d.busNumber,
            bookingUser: d.bookingUser || 'customer',
            source: d.source,
            destination: d.destination,
            numberOfSeats: d.numberOfSeats,
            totalFare: (d.numberOfSeats || 1) * 45,
            status: d.status || 'CONFIRMED',
            bookingDate: d.bookingDate || new Date().toISOString()
          }));
          this.orders.set(mapped);
        } else {
          this.orders.set([]);
        }
      },
      error: () => {
        this.isLoading.set(false);
        this.orders.set([]);
      }
    });
  }

  get filteredOrders(): CustomerOrder[] {
    if (this.filterStatus === 'ALL') return this.orders();
    return this.orders().filter(o => o.status === this.filterStatus);
  }

  backToFleet() {
    this.router.navigate(['/admin']);
  }
}
