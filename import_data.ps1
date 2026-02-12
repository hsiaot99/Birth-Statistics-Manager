# Configuration
$currentPath = Get-Location
$csvPath = Join-Path $currentPath "opendata112b210.csv"
$sqlServer = "localhost" # Default to localhost, change if using a named instance like ".\SQLEXPRESS"
$database = "BirthStats"

# Check if CSV exists
if (-not (Test-Path $csvPath)) {
    Write-Error "CSV file not found at $csvPath"
    exit 1
}

# Read CSV content with UTF-8 encoding
$csvData = Get-Content -Path $csvPath -Encoding UTF8 | Select-Object -Skip 1

# Create SQL for each row
$insertSql = @"
USE BirthStats;
GO

-- Drop existing table and recreate with optimized data types
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'BirthStatistics')
    DROP TABLE BirthStatistics;
GO

-- Create main table with optimized data types
CREATE TABLE BirthStatistics (
    StatYear SMALLINT,
    RecordType NVARCHAR(20),
    AreaCode CHAR(8),
    AreaName NVARCHAR(50),
    Gender CHAR(1),
    BirthWeight NVARCHAR(20),
    MultipleBirth NVARCHAR(10),
    BirthCount INT,
    ImportDate DATETIME DEFAULT GETDATE()
);
GO

"@

foreach ($line in $csvData) {
    $fields = $line -split ','
    if ($fields.Count -eq 8) {
        $insertSql += @"
INSERT INTO BirthStatistics (StatYear, RecordType, AreaCode, AreaName, Gender, BirthWeight, MultipleBirth, BirthCount)
VALUES ($($fields[0]), N'$($fields[1])', '$($fields[2])', N'$($fields[3])', 
    CASE N'$($fields[4])' WHEN N'男' THEN 'M' WHEN N'女' THEN 'F' ELSE 'U' END,
    N'$($fields[5])', N'$($fields[6])', $($fields[7]));

"@
    }
}

$insertSql += @"
-- Show count of imported records
SELECT COUNT(*) as TotalRecordsImported FROM BirthStatistics;
GO

-- Show sample data
SELECT TOP 5 * FROM BirthStatistics ORDER BY StatYear, AreaCode, BirthWeight;
GO

-- Show table space usage
EXEC sp_spaceused 'BirthStatistics';
GO
"@

# Write the SQL to a file
$insertSql | Out-File -FilePath "c:\Work\SQLOpen\generated_import.sql" -Encoding UTF8
