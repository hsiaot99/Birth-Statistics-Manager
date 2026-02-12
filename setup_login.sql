-- Enable mixed mode authentication
EXEC sp_configure 'contained database authentication', 1;
RECONFIGURE;
GO

-- Switch to master for server-level permissions
USE master;
GO

-- Drop existing login and user if they exist
IF EXISTS (SELECT * FROM sys.server_principals WHERE name = 'appuser')
BEGIN
    DROP LOGIN appuser;
END
GO

-- Create login with a simple password
-- SECURITY WARNING: Change this password for production use!
CREATE LOGIN appuser 
    WITH PASSWORD = 'App123Password',
    DEFAULT_DATABASE = BirthStats,
    CHECK_POLICY = OFF,
    CHECK_EXPIRATION = OFF;
GO

-- Grant server-level bulk insert permission
GRANT ADMINISTER BULK OPERATIONS TO appuser;
GO

-- Make sure database exists and is in multi-user mode
IF DB_ID('BirthStats') IS NULL
BEGIN
    CREATE DATABASE BirthStats;
END
GO

ALTER DATABASE BirthStats SET MULTI_USER;
GO

USE BirthStats;
GO

-- Drop and recreate user
IF EXISTS (SELECT * FROM sys.database_principals WHERE name = 'appuser')
BEGIN
    DROP USER appuser;
END
GO

-- Create user and grant permissions
CREATE USER appuser FOR LOGIN appuser;
GO

-- Grant basic permissions
EXEC sp_addrolemember 'db_datareader', 'appuser';
EXEC sp_addrolemember 'db_datawriter', 'appuser';
GO

-- Create test table if it doesn't exist
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'TestTable')
BEGIN
    CREATE TABLE TestTable (
        ID INT IDENTITY(1,1) PRIMARY KEY,
        TestValue NVARCHAR(100)
    );
    
    -- Insert a test row
    INSERT INTO TestTable (TestValue) VALUES ('Test Connection Successful');
END
GO

-- Test the login
EXECUTE AS LOGIN = 'appuser';
SELECT SYSTEM_USER AS CurrentLogin;
SELECT * FROM TestTable;
REVERT;
GO
