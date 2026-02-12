# Birth Statistics Manager

A Java Swing application for managing and analyzing birth statistics data. This application connects to a SQL Server database to import, export, and visualize birth statistics from various formats (CSV, JSON, XML).

## Features

- **Data Management**: View and manage birth statistics in a tabular format.
- **Import**: Bulk import data from CSV, JSON, and XML files.
- **Export**: Export current data to CSV, JSON, and XML formats.
- **Search**: Filter data by year or district code.
- **Database Integration**: Direct connection to SQL Server (MSSQL).

## Project Structure

```
BirthStats/
├── scr/                 # Source code (Java files)
├── lib/                 # Dependencies (MSSQL JDBC Drivers)
├── import_data.ps1      # PowerShell script for initial data loading
├── setup_login.sql      # SQL script for database user setup
├── config.properties    # Configuration file (ignored in git)
└── ...
```

## Prerequisites

- Java Development Kit (JDK) 8 or higher
- Microsoft SQL Server (Express or Standard)
- SQL Server Authentication enabled

## Setup

1.  **Database Setup**:
    - Run the SQL scripts in your SQL Server Management Studio (SSMS) or via sqlcmd to set up the database and user.
    - Check `setup_login.sql` for user creation (Default user: `appuser`).

2.  **Configuration**:
    - Copy `config.properties.template` to `config.properties`.
    - Edit `config.properties` with your database credentials:
      ```properties
      db.server=localhost
      db.port=1433
      db.name=BirthStats
      db.user=your_username
      db.password=your_password
      ```

3.  **Compile**:
    Open a terminal in the project root and run:
    ```bash
    javac -cp ".;lib/*" -d . scr/*.java
    ```

## Usage

To run the application:

```bash
java -cp ".;lib/*" BirthStatsManager
```

## Data Sources

The project includes sample data files (`opendata112b210.*`) sourced from open government data platforms.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
