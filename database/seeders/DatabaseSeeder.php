<?php

namespace Database\Seeders;

use App\Models\Admin;
use App\Models\User;
// use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    /**
     * Seed the application's database.
     */
    public function run(): void
    {
        // User::factory(10)->create();

        Admin::factory()->create([
            'name' => 'Test Admin',
            'email' => 'admin@mobilekit.com',
        ]);

        User::factory()->create([
            'name' => 'Test User',
            'email' => 'user@mobilekit.com',
        ]);
    }
}
