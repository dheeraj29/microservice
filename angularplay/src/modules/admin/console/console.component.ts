import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../../services/loginhandler.service';
import { Router } from '@angular/router';

export interface BusItem {
  busNumber: number;
  source: string;
  destination: string;
  price: string;
  totalSeats: string;
}

export interface FleetStats {
  totalBuses: number;
  activeRoutes: number;
  totalFleetCapacity: number;
  averageOccupancyRate: number;
  systemHealth: string;
}

@Component({
  selector: 'app-console',
  templateUrl: './console.component.html',
  styleUrl: './console.component.scss',
  standalone: false
})
export class ConsoleComponent implements OnInit {
  private http = inject(HttpClient);
  userService = inject(UserService);
  private router = inject(Router);

  // Fleet list and filtered items
  buses = signal<BusItem[]>([]);
  searchQuery: string = '';
  isLoading = signal<boolean>(false);

  // KPI Stats
  stats = signal<FleetStats>({
    totalBuses: 0,
    activeRoutes: 0,
    totalFleetCapacity: 0,
    averageOccupancyRate: 0,
    systemHealth: 'CONNECTING'
  });

  // Modal States
  activeModal = signal<'NONE' | 'ADD' | 'EDIT' | 'DELETE'>('NONE');
  selectedBusForAction = signal<BusItem | null>(null);

  // Form Model
  formBus: BusItem = {
    busNumber: 0,
    source: '',
    destination: '',
    price: '45',
    totalSeats: '40'
  };

  // Toast Notification
  toastMessage = signal<{ type: 'success' | 'error'; text: string } | null>(null);

  ngOnInit() {
    this.loadFleet();
    this.loadStats();
  }

  loadFleet() {
    this.isLoading.set(true);
    this.http.get<BusItem[]>('/adminservice/v1/allBuses').subscribe({
      next: (data) => {
        this.isLoading.set(false);
        this.buses.set(data || []);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.buses.set([]);
        this.showToast('error', 'Failed to retrieve fleet list from Admin Service.');
      }
    });
  }

  loadStats() {
    this.http.get<FleetStats>('/adminservice/v1/dashboardStats').subscribe({
      next: (data) => {
        if (data) this.stats.set(data);
      },
      error: () => {
        this.stats.set({
          totalBuses: 0,
          activeRoutes: 0,
          totalFleetCapacity: 0,
          averageOccupancyRate: 0,
          systemHealth: 'OFFLINE'
        });
      }
    });
  }

  get filteredBuses(): BusItem[] {
    const query = this.searchQuery.toLowerCase().trim();
    if (!query) return this.buses();
    return this.buses().filter(b => 
      b.busNumber.toString().includes(query) ||
      b.source.toLowerCase().includes(query) ||
      b.destination.toLowerCase().includes(query)
    );
  }

  openAddModal() {
    this.formBus = {
      busNumber: Math.floor(100 + Math.random() * 900),
      source: '',
      destination: '',
      price: '45',
      totalSeats: '40'
    };
    this.activeModal.set('ADD');
  }

  openEditModal(bus: BusItem) {
    this.selectedBusForAction.set(bus);
    this.formBus = { ...bus };
    this.activeModal.set('EDIT');
  }

  openDeleteModal(bus: BusItem) {
    this.selectedBusForAction.set(bus);
    this.activeModal.set('DELETE');
  }

  closeModal() {
    this.activeModal.set('NONE');
    this.selectedBusForAction.set(null);
  }

  saveBus() {
    if (!this.formBus.source || !this.formBus.destination) {
      this.showToast('error', 'Please fill in both Origin and Destination');
      return;
    }

    if (this.activeModal() === 'ADD') {
      this.http.post('/adminservice/v1/addBusDetails', this.formBus, { responseType: 'text' }).subscribe({
        next: () => {
          this.showToast('success', `Coach #${this.formBus.busNumber} added to fleet!`);
          this.closeModal();
          this.loadFleet();
          this.loadStats();
        },
        error: () => {
          this.showToast('error', `Failed to add coach #${this.formBus.busNumber} in Admin Service.`);
        }
      });
    } else if (this.activeModal() === 'EDIT') {
      const p = new URLSearchParams();
      p.set('busNumber', this.formBus.busNumber.toString());
      p.set('source', this.formBus.source);
      p.set('destination', this.formBus.destination);
      p.set('price', this.formBus.price);

      this.http.post('/adminservice/v1/updateBusDetails?' + p.toString(), {}).subscribe({
        next: () => {
          this.showToast('success', `Coach #${this.formBus.busNumber} details updated!`);
          this.closeModal();
          this.loadFleet();
        },
        error: () => {
          this.showToast('error', `Failed to update coach #${this.formBus.busNumber}.`);
        }
      });
    }
  }

  confirmDelete() {
    const bus = this.selectedBusForAction();
    if (!bus) return;

    this.http.delete(`/adminservice/v1/deleteBusDetails?busNumber=${bus.busNumber}`, { responseType: 'text' }).subscribe({
      next: () => {
        this.showToast('success', `Coach #${bus.busNumber} removed from fleet.`);
        this.closeModal();
        this.loadFleet();
        this.loadStats();
      },
      error: () => {
        this.showToast('error', `Failed to delete coach #${bus.busNumber}.`);
      }
    });
  }

  showToast(type: 'success' | 'error', text: string) {
    this.toastMessage.set({ type, text });
    setTimeout(() => this.toastMessage.set(null), 4000);
  }
}
