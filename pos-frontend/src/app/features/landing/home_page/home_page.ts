import { Component } from '@angular/core';
import { Navbar } from '../../../shared/components/navbar/navbar';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [Navbar],
  templateUrl: './home_page.html',
  styleUrl: './home_page.css',
})
export class HomePage {}
