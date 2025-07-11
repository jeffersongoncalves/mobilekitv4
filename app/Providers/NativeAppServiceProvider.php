<?php

namespace App\Providers;

use Native\Laravel\Contracts\ProvidesPhpIni;
use Native\Laravel\Facades\Menu;
use Native\Laravel\Facades\Window;

class NativeAppServiceProvider implements ProvidesPhpIni
{
    /**
     * Executed once the native application has been booted.
     * Use this method to open windows, register global shortcuts, etc.
     */
    public function boot(): void
    {
        $panels = [];

        if (config('mobilekit.admin_panel_enabled', false)) {
            $panels[] = Menu::route('filament.admin.auth.login', 'Admin Panel');
        }
        if (config('mobilekit.app_panel_enabled', false)) {
            $panels[] = Menu::route('filament.app.auth.login', 'App Panel');
        }
        if (config('mobilekit.guest_panel_enabled', false)) {
            $panels[] = Menu::route('filament.guest.pages.dashboard', 'Guest Panel');
        }

        Menu::create(
            Menu::make(...$panels)->label('Panels'),
            Menu::file(),
            Menu::view(),
        );

        Window::open()
            ->maximized();
    }

    /**
     * Return an array of php.ini directives to be set.
     */
    public function phpIni(): array
    {
        return [
        ];
    }
}
