USE ambulance_transfer;
GO
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
GO
IF OBJECT_ID(N'dbo.car_company', N'U') IS NOT NULL
    THROW 50001, N'核心表已存在，请使用后续迁移脚本，不要重复初始化。', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    CREATE TABLE dbo.car_company (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_car_company PRIMARY KEY,
        name NVARCHAR(100) NOT NULL,
        license_no NVARCHAR(100) NOT NULL CONSTRAINT UQ_company_license UNIQUE,
        license_expire_at DATE NULL,
        audit_status VARCHAR(20) NOT NULL CONSTRAINT DF_company_audit DEFAULT 'PENDING',
        audit_remark NVARCHAR(500) NULL,
        contact_phone VARCHAR(30) NOT NULL,
        created_at DATETIME2(3) NOT NULL CONSTRAINT DF_company_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_company_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_company_audit CHECK (audit_status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
        CONSTRAINT CK_company_rejection CHECK (audit_status <> 'REJECTED' OR (audit_remark IS NOT NULL AND LEN(LTRIM(RTRIM(audit_remark))) > 0))
    );

    CREATE TABLE dbo.vehicle (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vehicle PRIMARY KEY,
        company_id BIGINT NOT NULL,
        plate_no NVARCHAR(20) NOT NULL CONSTRAINT UQ_vehicle_plate UNIQUE,
        service_level VARCHAR(20) NOT NULL,
        status VARCHAR(20) NOT NULL CONSTRAINT DF_vehicle_status DEFAULT 'IDLE',
        mock_lat DECIMAL(10,7) NULL,
        mock_lng DECIMAL(10,7) NULL,
        created_at DATETIME2(3) NOT NULL CONSTRAINT DF_vehicle_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_vehicle_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_vehicle_company FOREIGN KEY (company_id) REFERENCES dbo.car_company(id),
        CONSTRAINT UQ_vehicle_company_id UNIQUE (company_id, id),
        CONSTRAINT CK_vehicle_level CHECK (service_level IN ('BASIC', 'MONITORING')),
        CONSTRAINT CK_vehicle_status CHECK (status IN ('IDLE', 'TASK', 'MAINTENANCE')),
        CONSTRAINT CK_vehicle_lat CHECK (mock_lat BETWEEN -90 AND 90),
        CONSTRAINT CK_vehicle_lng CHECK (mock_lng BETWEEN -180 AND 180)
    );

    CREATE TABLE dbo.staff (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_staff PRIMARY KEY,
        company_id BIGINT NULL,
        name NVARCHAR(50) NOT NULL,
        role VARCHAR(20) NOT NULL,
        phone VARCHAR(30) NOT NULL,
        username NVARCHAR(50) NULL,
        password_hash VARCHAR(100) NULL,
        enabled BIT NOT NULL CONSTRAINT DF_staff_enabled DEFAULT 1,
        cert_no NVARCHAR(100) NULL,
        cert_expire_at DATE NULL,
        created_at DATETIME2(3) NOT NULL CONSTRAINT DF_staff_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_staff_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_staff_company FOREIGN KEY (company_id) REFERENCES dbo.car_company(id),
        CONSTRAINT UQ_staff_company_id UNIQUE (company_id, id),
        CONSTRAINT CK_staff_role CHECK (role IN ('ADMIN', 'DISPATCHER', 'DRIVER', 'NURSE')),
        CONSTRAINT CK_staff_company CHECK ((role = 'ADMIN' AND company_id IS NULL) OR (role <> 'ADMIN' AND company_id IS NOT NULL)),
        CONSTRAINT CK_staff_credentials CHECK ((username IS NULL AND password_hash IS NULL) OR (username IS NOT NULL AND password_hash IS NOT NULL))
    );
    CREATE UNIQUE INDEX UX_staff_username ON dbo.staff(username) WHERE username IS NOT NULL;

    CREATE TABLE dbo.transfer_order (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_transfer_order PRIMARY KEY,
        order_no VARCHAR(40) NOT NULL CONSTRAINT UQ_order_number UNIQUE,
        user_name NVARCHAR(50) NOT NULL,
        user_phone VARCHAR(30) NOT NULL,
        origin_address NVARCHAR(255) NOT NULL,
        origin_lat DECIMAL(10,7) NULL,
        origin_lng DECIMAL(10,7) NULL,
        dest_address NVARCHAR(255) NOT NULL,
        dest_lat DECIMAL(10,7) NULL,
        dest_lng DECIMAL(10,7) NULL,
        service_level VARCHAR(20) NOT NULL,
        patient_note NVARCHAR(1000) NULL,
        status VARCHAR(20) NOT NULL CONSTRAINT DF_order_status DEFAULT 'PENDING_QUOTE',
        assigned_company_id BIGINT NULL,
        assigned_vehicle_id BIGINT NULL,
        assigned_driver_id BIGINT NULL,
        assigned_nurse_id BIGINT NULL,
        final_amount DECIMAL(12,2) NULL,
        actual_amount DECIMAL(12,2) NULL,
        fee_remark NVARCHAR(500) NULL,
        cancel_reason NVARCHAR(500) NULL,
        may_charge_empty_run BIT NOT NULL CONSTRAINT DF_order_empty_run DEFAULT 0,
        created_at DATETIME2(3) NOT NULL CONSTRAINT DF_order_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_order_updated DEFAULT SYSUTCDATETIME(),
        row_version ROWVERSION NOT NULL,
        CONSTRAINT FK_order_company FOREIGN KEY (assigned_company_id) REFERENCES dbo.car_company(id),
        CONSTRAINT FK_order_vehicle FOREIGN KEY (assigned_company_id, assigned_vehicle_id) REFERENCES dbo.vehicle(company_id, id),
        CONSTRAINT FK_order_driver FOREIGN KEY (assigned_company_id, assigned_driver_id) REFERENCES dbo.staff(company_id, id),
        CONSTRAINT FK_order_nurse FOREIGN KEY (assigned_company_id, assigned_nurse_id) REFERENCES dbo.staff(company_id, id),
        CONSTRAINT CK_order_assignment CHECK (assigned_company_id IS NOT NULL OR (assigned_vehicle_id IS NULL AND assigned_driver_id IS NULL AND assigned_nurse_id IS NULL)),
        CONSTRAINT CK_order_level CHECK (service_level IN ('BASIC', 'MONITORING')),
        CONSTRAINT CK_order_status CHECK (status IN ('PENDING_QUOTE', 'QUOTING', 'CONFIRMED', 'DISPATCHED', 'EN_ROUTE', 'ARRIVED', 'ON_BOARD', 'COMPLETED', 'CANCELLED')),
        CONSTRAINT CK_order_final_amount CHECK (final_amount >= 0),
        CONSTRAINT CK_order_actual_amount CHECK (actual_amount >= 0),
        CONSTRAINT CK_order_origin_lat CHECK (origin_lat BETWEEN -90 AND 90),
        CONSTRAINT CK_order_origin_lng CHECK (origin_lng BETWEEN -180 AND 180),
        CONSTRAINT CK_order_dest_lat CHECK (dest_lat BETWEEN -90 AND 90),
        CONSTRAINT CK_order_dest_lng CHECK (dest_lng BETWEEN -180 AND 180)
    );

    CREATE TABLE dbo.quote (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_quote PRIMARY KEY,
        order_id BIGINT NOT NULL,
        company_id BIGINT NOT NULL,
        amount DECIMAL(12,2) NOT NULL,
        remark NVARCHAR(500) NULL,
        status VARCHAR(20) NOT NULL CONSTRAINT DF_quote_status DEFAULT 'PENDING',
        created_at DATETIME2(3) NOT NULL CONSTRAINT DF_quote_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_quote_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_quote_order FOREIGN KEY (order_id) REFERENCES dbo.transfer_order(id),
        CONSTRAINT FK_quote_company FOREIGN KEY (company_id) REFERENCES dbo.car_company(id),
        CONSTRAINT UQ_quote_order_company UNIQUE (order_id, company_id),
        CONSTRAINT CK_quote_amount CHECK (amount > 0),
        CONSTRAINT CK_quote_status CHECK (status IN ('PENDING', 'WON', 'LOST'))
    );
    CREATE UNIQUE INDEX UX_quote_one_winner ON dbo.quote(order_id) WHERE status = 'WON';

    CREATE TABLE dbo.order_status_log (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_order_status_log PRIMARY KEY,
        order_id BIGINT NOT NULL,
        from_status VARCHAR(20) NULL,
        to_status VARCHAR(20) NOT NULL,
        operator_type VARCHAR(20) NOT NULL,
        operator_id BIGINT NULL,
        event_type VARCHAR(20) NOT NULL CONSTRAINT DF_log_event DEFAULT 'STATUS_CHANGE',
        remark NVARCHAR(1000) NULL,
        created_at DATETIME2(3) NOT NULL CONSTRAINT DF_log_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_log_order FOREIGN KEY (order_id) REFERENCES dbo.transfer_order(id),
        CONSTRAINT CK_log_from CHECK (from_status IN ('PENDING_QUOTE', 'QUOTING', 'CONFIRMED', 'DISPATCHED', 'EN_ROUTE', 'ARRIVED', 'ON_BOARD', 'COMPLETED', 'CANCELLED')),
        CONSTRAINT CK_log_to CHECK (to_status IN ('PENDING_QUOTE', 'QUOTING', 'CONFIRMED', 'DISPATCHED', 'EN_ROUTE', 'ARRIVED', 'ON_BOARD', 'COMPLETED', 'CANCELLED')),
        CONSTRAINT CK_log_operator CHECK (operator_type IN ('ADMIN', 'DISPATCHER', 'DRIVER', 'MOCK_USER', 'SYSTEM')),
        CONSTRAINT CK_log_event CHECK (event_type IN ('STATUS_CHANGE', 'FEE_CHANGE'))
    );

    CREATE INDEX IX_vehicle_availability ON dbo.vehicle(company_id, service_level, status);
    CREATE INDEX IX_staff_company_role ON dbo.staff(company_id, role);
    CREATE INDEX IX_order_pool ON dbo.transfer_order(status, service_level, created_at);
    CREATE INDEX IX_order_company ON dbo.transfer_order(assigned_company_id, status, created_at);
    CREATE INDEX IX_order_driver ON dbo.transfer_order(assigned_driver_id, status);
    CREATE INDEX IX_quote_company ON dbo.quote(company_id, status);
    CREATE INDEX IX_log_timeline ON dbo.order_status_log(order_id, created_at, id);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
