/*
Birth Statistics Data Import Script
---------------------------------
This script imports birth statistics data from a CSV file into SQL Server.

Requirements:
- SQL Server with appropriate permissions
- CSV file: opendata112b210.csv in UTF-8 encoding
- IMPORTANT: You must update the file path in the 'BULK INSERT' section 
  to match the absolute path of the CSV file on your system.

Author: Cascade AI
Date: 2025-01-07
*/

-- Use BirthStats database
USE BirthStats;
GO

-- Enable advanced options for bulk insert
sp_configure 'show advanced options', 1;
RECONFIGURE;
GO

-- Drop existing table if it exists
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'BirthStatistics')
    DROP TABLE BirthStatistics;
GO

-- Create BirthStatistics table
CREATE TABLE BirthStatistics (
    ID INT IDENTITY(1,1) PRIMARY KEY,
    StatYear INT,                  -- Statistical year
    RecordType NVARCHAR(100),     -- Record type (e.g., by registration date)
    AreaCode NVARCHAR(20),        -- Area code
    AreaName NVARCHAR(100),       -- Area name
    Gender NVARCHAR(10),          -- Gender (男/女)
    BirthWeight NVARCHAR(50),     -- Birth weight category
    MultipleBirth NVARCHAR(20),   -- Multiple birth category
    BirthCount INT,               -- Number of births
    ImportDate DATETIME DEFAULT GETDATE()  -- Import timestamp
);
GO

-- Create staging table with matching structure
CREATE TABLE #RawData (
    StatYear INT,
    RecordType NVARCHAR(100),
    AreaCode NVARCHAR(20),
    AreaName NVARCHAR(100),
    Gender NVARCHAR(10),
    BirthWeight NVARCHAR(50),
    MultipleBirth NVARCHAR(20),
    BirthCount INT
);
GO

-- Bulk insert data from CSV
-- NOTE: SQL Server requires the full absolute path to the file.
-- Please update the path below to where you have saved 'opendata112b210.csv'.
BULK INSERT #RawData
FROM 'C:\Path\To\Your\BirthStats\opendata112b210.csv'
WITH (
    CODEPAGE = '65001',           -- UTF-8 encoding
    DATAFILETYPE = 'char',
    FIELDTERMINATOR = ',',
    ROWTERMINATOR = '\n',
    FIRSTROW = 2                  -- Skip header row
);
GO

-- Insert and clean data
INSERT INTO BirthStatistics (
    StatYear,
    RecordType,
    AreaCode,
    AreaName,
    Gender,
    BirthWeight,
    MultipleBirth,
    BirthCount
)
SELECT 
    StatYear,
    TRIM(RecordType),
    TRIM(AreaCode),
    TRIM(AreaName),
    TRIM(Gender),
    -- Standardize weight categories
    CASE 
        WHEN TRIM(BirthWeight) = N'1000克及以下' THEN N'1000g and below'
        WHEN TRIM(BirthWeight) = N'5001克及以上' THEN N'5001g and above'
        WHEN TRIM(BirthWeight) LIKE N'%克' THEN 
            REPLACE(REPLACE(TRIM(BirthWeight), N'克', 'g'), N'～', '-')
        ELSE TRIM(BirthWeight)
    END as BirthWeight,
    TRIM(MultipleBirth),
    BirthCount
FROM #RawData
WHERE StatYear IS NOT NULL;
GO

-- Data validation and summary
-- 1. Show total number of records
SELECT COUNT(*) as TotalRecords FROM BirthStatistics;

-- 2. Show sample records
SELECT TOP 20 * 
FROM BirthStatistics 
WHERE BirthCount > 0
ORDER BY StatYear, AreaCode, BirthWeight;

-- 3. Summary by gender and birth weight
SELECT 
    Gender,
    BirthWeight,
    COUNT(*) as Records,
    SUM(BirthCount) as TotalBirths,
    AVG(CAST(BirthCount as FLOAT)) as AvgBirthsPerRecord
FROM BirthStatistics
WHERE BirthCount > 0
GROUP BY Gender, BirthWeight
ORDER BY 
    Gender,
    CASE 
        WHEN BirthWeight = '1000g and below' THEN 1
        WHEN BirthWeight = '5001g and above' THEN 999
        ELSE CAST(SUBSTRING(BirthWeight, 1, CHARINDEX('-', BirthWeight + '-') - 1) AS INT)
    END;

-- 4. Summary by area
SELECT 
    AreaName,
    COUNT(*) as Records,
    SUM(BirthCount) as TotalBirths
FROM BirthStatistics
WHERE BirthCount > 0
GROUP BY AreaName
ORDER BY TotalBirths DESC;

-- Cleanup
DROP TABLE #RawData;
GO

-- Reset advanced options
sp_configure 'show advanced options', 0;
RECONFIGURE;
GO
