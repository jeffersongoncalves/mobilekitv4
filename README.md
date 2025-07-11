<div class="filament-hidden">

![MobileKit](https://raw.githubusercontent.com/jeffersongoncalves/mobilekitv4/4.x/art/jeffersongoncalves-mobilekitv4.png)

</div>

# MobileKit Start Kit NativePHP 1.x, Filament 4.x and Laravel 12.x

## About MobileKit

MobileKit is a robust starter kit built on Laravel 12.x, Filament 4.x and NativePHP 1.x, designed to accelerate the development of modern
desktop and mobile applications with a ready-to-use multi-panel structure.

## Features

- **Laravel 12.x** - The latest version of the most elegant PHP framework
- **Filament 4.x** - Powerful and flexible admin framework
- **NativePHP 1.x** - Build native desktop and mobile applications using PHP
  - **Desktop Support** - Create native desktop applications with Electron
  - **Mobile Support** - Build native mobile applications for Android and iOS
- **Multi-Panel Structure** - Includes three pre-configured panels:
    - Admin Panel (`/admin`) - For system administrators
    - App Panel (`/app`) - For authenticated application users
    - Public Panel (frontend interface) - For visitors
- **Environment Configuration** - Centralized configuration through the `config/mobilekit.php` file

## System Requirements

### Basic Requirements
- PHP 8.2 or higher
- Composer
- Node.js and PNPM

### Additional Requirements for Mobile Development
- **Android Development**: Android Studio, Android SDK, JDK 11+
- **iOS Development**: Xcode (macOS only), iOS SDK, JDK 11+

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
- `app/Providers/Filament/PublicPanelProvider.php`

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

MobileKit includes support for:

- User and admin management
- Multi-guard authentication system
- Tailwind CSS integration
- Database queue configuration
- Customizable panel routing and branding
- Native desktop application development (Electron)
- Native mobile application development (Android/iOS)
- Cross-platform deployment capabilities

## License

This project is licensed under the [MIT License](LICENSE).

## Credits

Developed by [Jefferson Gonçalves](https://github.com/jeffersongoncalves).
