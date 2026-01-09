<div class="filament-hidden">

![MobileKit](https://raw.githubusercontent.com/jeffersongoncalves/mobilekitv4/main/art/jeffersongoncalves-mobilekitv4.png)

</div>

# MobileKit Start Kit NativePHP 1.x, Filament 4.x and Laravel 12.x

## About MobileKit

MobileKit is a robust starter kit built on Laravel 12.x, Filament 4.x and NativePHP 1.x, designed to accelerate the development of modern
native desktop and mobile applications with a ready-to-use multi-panel structure. Build native iOS and Android applications using PHP,
alongside desktop applications for Windows, macOS, and Linux.

## Features

- **Laravel 12.x** - The latest version of the most elegant PHP framework
- **Filament 4.x** - Powerful and flexible admin framework
- **NativePHP 1.x** - Build native desktop and mobile applications with PHP
- **Mobile Development** - Native iOS and Android app development with NativePHP Mobile
- **Desktop Development** - Native Windows, macOS, and Linux applications with NativePHP Electron
- **Multi-Panel Structure** - Includes three pre-configured panels:
    - Admin Panel (`/admin`) - For system administrators
    - App Panel (`/app`) - For authenticated application users
    - Guest Panel (frontend interface) - For visitors
- **Mobile Configuration** - Comprehensive mobile app settings including app ID, version management, deep linking, and platform-specific configurations
- **Cross-Platform Support** - Single codebase for iOS, Android, Windows, macOS, and Linux
- **Environment Configuration** - Centralized configuration through the `config/mobilekit.php` file
- **Testing Framework** - Pest PHP for elegant testing
- **Code Quality Tools** - Laravel Pint for code formatting and Larastan for static analysis
- **Development Tools** - IDE Helper, Laravel Pail for log monitoring

## System Requirements

### General Requirements
- PHP 8.2 or higher
- Composer
- Node.js and PNPM

### Desktop Development
- No additional requirements

### Mobile Development
- **Android Development:**
    - Android SDK
    - Android Studio (recommended)
    - Java Development Kit (JDK) 11 or higher
- **iOS Development (macOS only):**
    - Xcode 14 or higher
    - iOS Simulator
    - Apple Developer Account (for device testing and App Store distribution)

## Installation

Clone the repository
``` bash
laravel new my-app --using=jeffersongoncalves/mobilekitv4
```

###  Easy Installation

MobileKit can be easily installed using the following command:

```bash
php install.php
```

This command automates the installation process by:
- Installing Composer dependencies
- Setting up the environment file
- Generating application key
- Setting up the database
- Running migrations
- Installing Node.js dependencies
- Building assets

### Manual Installation

Install JavaScript dependencies
``` bash
pnpm install
```
Install Composer dependencies
``` bash
composer install
```
Set up environment
``` bash
cp .env.example .env
php artisan key:generate
```

Configure your database in the .env file

Run migrations
``` bash
php artisan native:migrate
```
Run the server
``` bash
php artisan native:serve
```

## Authentication Structure

MobileKit comes pre-configured with a custom authentication system that supports different types of users:

- `Admin` - For administrative panel access
- `User` - For application panel access

## Development

### Desktop Development

``` bash
# Run the development server with logs, queues and asset compilation
composer native:dev

# Or run each component separately
php artisan native:serve
pnpm run dev
```

### Mobile Development

For mobile application development, NativePHP Mobile provides additional commands:

``` bash
# Build for Android
php artisan native:android

# Build for iOS (macOS only)
php artisan native:ios

# Run mobile development server
php artisan native:serve --mobile
```

**Mobile Development Requirements:**
- **Android**: Android Studio and Android SDK
- **iOS**: Xcode (macOS only) and iOS SDK
- **Both**: Java Development Kit (JDK) 11 or higher

## Customization

### Panel Configuration

Panels can be customized through their respective providers:

- `app/Providers/Filament/AdminPanelProvider.php`
- `app/Providers/Filament/AppPanelProvider.php`
- `app/Providers/Filament/GuestPanelProvider.php`

Alternatively, these settings are also consolidated in the `config/mobilekit.php` file for easier management.

### Themes and Colors

Each panel can have its own color scheme, which can be easily modified in the corresponding Provider files or in the
`mobilekit.php` configuration file.

### Configuration File

The `config/mobilekit.php` file centralizes the configuration of the starter kit, including:

- Panel routes
- Middleware for each panel
- Branding options (logo, colors)
- Authentication guards

## Resources

MobileKit includes comprehensive support for:

- **User Management** - User and admin management with multi-guard authentication
- **UI Framework** - Tailwind CSS integration with Filament components
- **Queue System** - Database queue configuration for background processing
- **Panel System** - Customizable panel routing and branding
- **Desktop Development** - Native desktop application development with NativePHP
- **Mobile Development** - Native mobile application support
- **Cross-Platform** - Full compatibility across Windows, macOS, Linux, iOS, and Android
- **Testing Suite** - Pest PHP testing framework with Laravel integration
- **Code Quality** - Laravel Pint formatting and Larastan static analysis
- **Development Tools** - IDE helpers, log monitoring with Laravel Pail
- **Asset Management** - Vite integration for modern asset compilation

## License

This project is licensed under the [MIT License](LICENSE).

## Credits

Developed by [Jefferson Gonçalves](https://github.com/jeffersongoncalves).
