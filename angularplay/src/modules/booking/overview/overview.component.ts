import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../../services/loginhandler.service';
import { Router } from '@angular/router';

export interface BusRoute {
  busNumber: number;
  source: string;
  destination: string;
  price: string;
  totalSeats: string;
  departureTime?: string;
  arrivalTime?: string;
}

export interface BusSeat {
  seatNumber: number;
  seatLabel: string;
  status: 'AVAILABLE' | 'OCCUPIED' | 'SELECTED';
  type: 'WINDOW' | 'AISLE';
  price: number;
}

export interface BookingRecord {
  bookingNumber?: number;
  bookingId?: number;
  busNumber: number;
  bookingUser: string;
  source: string;
  destination: string;
  numberOfSeats: number;
  status: string;
  bookingDate?: string;
}

@Component({
  selector: 'app-overview',
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.scss',
  standalone: false
})
export class OverviewComponent implements OnInit {
  private http = inject(HttpClient);
  userService = inject(UserService);
  private router = inject(Router);

  // Active view tab
  activeTab = signal<'SEARCH' | 'MY_BOOKINGS'>('SEARCH');

  // Search parameters
  searchSource: string = 'New York';
  searchDestination: string = 'Boston';
  searchDate: string = new Date().toISOString().split('T')[0];

  // Available Buses
  availableBuses = signal<BusRoute[]>([]);
  isLoadingBuses = signal<boolean>(false);
  selectedBus = signal<BusRoute | null>(null);

  // Seat Matrix for selected bus
  seatGrid = signal<BusSeat[]>([]);
  selectedSeatLabels = signal<string[]>([]);
  isBookingInProgress = signal<boolean>(false);

  // Customer Bookings
  myBookings = signal<BookingRecord[]>([]);
  isLoadingBookings = signal<boolean>(false);

  // Modals & Notifications
  activeModal = signal<'NONE' | 'CONFIRMATION' | 'BOARDING_PASS'>('NONE');
  latestConfirmedBooking = signal<BookingRecord | null>(null);
  activeBoardingPass = signal<BookingRecord | null>(null);
  toastMessage = signal<{ type: 'success' | 'error'; text: string } | null>(null);

  ngOnInit() {
    this.searchRoutes();
    this.loadMyBookings();
  }

  setTab(tab: 'SEARCH' | 'MY_BOOKINGS') {
    this.activeTab.set(tab);
    if (tab === 'MY_BOOKINGS') {
      this.loadMyBookings();
    }
  }

  searchRoutes() {
    this.isLoadingBuses.set(true);
    this.selectedBus.set(null);
    this.selectedSeatLabels.set([]);

    this.http.get<BusRoute[]>('/adminservice/v1/allBuses').subscribe({
      next: (buses) => {
        this.isLoadingBuses.set(false);
        if (buses && buses.length > 0) {
          const filtered = buses.filter(b => 
            (!this.searchSource || b.source?.toLowerCase().includes(this.searchSource.toLowerCase())) &&
            (!this.searchDestination || b.destination?.toLowerCase().includes(this.searchDestination.toLowerCase()))
          );
          this.availableBuses.set(filtered.length > 0 ? filtered : buses);
        } else {
          this.availableBuses.set([]);
        }
      },
      error: (err) => {
        this.isLoadingBuses.set(false);
        this.availableBuses.set([]);
        this.showToast('error', 'Unable to fetch bus routes from Admin Service.');
      }
    });
  }

  selectBus(bus: BusRoute) {
    this.selectedBus.set(bus);
    this.selectedSeatLabels.set([]);
    this.loadSeatGrid(bus.busNumber);
  }

  loadSeatGrid(busNumber: number) {
    this.http.get<BusSeat[]>(`/inventoryservice/v1/busSeatLayout/${busNumber}`).subscribe({
      next: (seats) => {
        this.seatGrid.set(seats || []);
      },
      error: () => {
        this.showToast('error', `Failed to load seat layout for Bus #${busNumber} from Inventory Service.`);
        this.seatGrid.set([]);
      }
    });
  }

  toggleSeat(seat: BusSeat) {
    if (seat.status === 'OCCUPIED') return;

    const currentSelected = [...this.selectedSeatLabels()];
    const index = currentSelected.indexOf(seat.seatLabel);

    if (index > -1) {
      currentSelected.splice(index, 1);
      seat.status = 'AVAILABLE';
    } else {
      currentSelected.push(seat.seatLabel);
      seat.status = 'SELECTED';
    }
    this.selectedSeatLabels.set(currentSelected);
  }

  confirmBooking() {
    const bus = this.selectedBus();
    const seats = this.selectedSeatLabels();
    const currentUser = this.userService.currentUser();
    if (!bus || seats.length === 0 || !currentUser) return;

    this.isBookingInProgress.set(true);

    const payload = new URLSearchParams();
    payload.set('source', bus.source);
    payload.set('destination', bus.destination);
    payload.set('requiredSeats', seats.length.toString());
    payload.set('busNumber', bus.busNumber.toString());
    payload.set('bookingUser', currentUser.username);

    this.http.post<BookingRecord>('/bookingservice/v1/bookSeat?' + payload.toString(), {}).subscribe({
      next: (result) => {
        this.isBookingInProgress.set(false);
        this.latestConfirmedBooking.set(result);
        this.activeModal.set('CONFIRMATION');
        this.loadMyBookings();
        const id = result.bookingNumber || result.bookingId;
        this.showToast('success', `Booking #${id} confirmed!`);
      },
      error: (err) => {
        this.isBookingInProgress.set(false);
        this.showToast('error', 'Booking request failed in Booking Service / Saga execution.');
      }
    });
  }

  loadMyBookings() {
    const currentUser = this.userService.currentUser();
    if (!currentUser) return;

    this.isLoadingBookings.set(true);
    this.http.get<BookingRecord[]>(`/bookingservice/v1/myBookings?username=${encodeURIComponent(currentUser.username)}`).subscribe({
      next: (bookings) => {
        this.isLoadingBookings.set(false);
        this.myBookings.set(bookings || []);
      },
      error: (err) => {
        this.isLoadingBookings.set(false);
        this.myBookings.set([]);
      }
    });
  }

  cancelTicket(booking: BookingRecord) {
    const currentUser = this.userService.currentUser();
    const id = booking.bookingNumber ?? booking.bookingId;
    if (!id || !currentUser) return;

    this.http.post(`/bookingservice/v1/cancelBooking?bookingNumber=${id}&username=${encodeURIComponent(currentUser.username)}`, {}, { responseType: 'text' }).subscribe({
      next: () => {
        booking.status = 'CANCELLED';
        this.showToast('success', `Booking #${id} has been successfully cancelled.`);
      },
      error: () => {
        this.showToast('error', `Failed to cancel booking #${id}.`);
      }
    });
  }

  viewBoardingPass(booking: BookingRecord) {
    this.activeBoardingPass.set(booking);
    this.activeModal.set('BOARDING_PASS');
  }

  closeModal() {
    this.activeModal.set('NONE');
    this.selectedBus.set(null);
    this.selectedSeatLabels.set([]);
  }

  showToast(type: 'success' | 'error', text: string) {
    this.toastMessage.set({ type, text });
    setTimeout(() => this.toastMessage.set(null), 4000);
  }

  backNavigation() {
    this.router.navigate(['/']);
  }
}