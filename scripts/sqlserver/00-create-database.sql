USE master;
GO
IF DB_ID(N'ambulance_transfer') IS NULL
BEGIN
    CREATE DATABASE ambulance_transfer;
END;
GO
