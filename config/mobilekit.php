<?php

use Filament\Enums\ThemeMode;

return [
    'defaultCurrency' => 'usd',
    'defaultDateDisplayFormat' => 'M j, Y',
    'defaultIsoDateDisplayFormat' => 'L',
    'defaultDateTimeDisplayFormat' => 'M j, Y H:i:s',
    'defaultIsoDateTimeDisplayFormat' => 'LLL',
    'defaultNumberLocale' => null,
    'defaultTimeDisplayFormat' => 'H:i:s',
    'defaultIsoTimeDisplayFormat' => 'LT',
    'theme_mode' => ThemeMode::Light,
    'guest_panel_enabled' => true,
    'admin_panel_enabled' => true,
    'app_panel_enabled' => true,
    'favicon' => [
        'logo' => 'resources/images/logo-mobilekit.png',
    ],
];
