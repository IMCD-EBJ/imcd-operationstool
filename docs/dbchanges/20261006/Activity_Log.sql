USE [OPERATIONSTOOL]
GO

/*
    Activity log for Operations Tool.
    One row is one completed action. LogId is the only identifier.
    There is no end date and no status: the row exists because the action finished.
    TaskName is the action.
*/

IF OBJECT_ID(N'dbo.Activity_Log', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Activity_Log
    (
        LogId        INT IDENTITY(1,1) NOT NULL,
        DateInit     DATETIME          NOT NULL CONSTRAINT DF_Activity_Log_DateInit DEFAULT (GETDATE()),
        LocalADUser  NVARCHAR(200)     NOT NULL,
        UserName     NVARCHAR(200)     NULL,
        TaskName     NVARCHAR(50)      NOT NULL,
        CONSTRAINT PK_Activity_Log PRIMARY KEY CLUSTERED (LogId),
        CONSTRAINT CK_Activity_Log_TaskName CHECK (
            TaskName IN (N'LOGIN', N'IMPORT_FILE', N'EXPORT_DATA', N'APPLY_FILTERS')
        )
    );
END
GO

IF OBJECT_ID(N'dbo.CK_Activity_Log_TaskName', N'C') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Activity_Log DROP CONSTRAINT CK_Activity_Log_TaskName;
END
GO

ALTER TABLE dbo.Activity_Log ADD CONSTRAINT CK_Activity_Log_TaskName CHECK (
    TaskName IN (N'LOGIN', N'IMPORT_FILE', N'EXPORT_DATA', N'APPLY_FILTERS')
);
GO

IF COL_LENGTH(N'dbo.Activity_Log', N'Detail') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Activity_Log DROP COLUMN Detail;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_Activity_Log_DateInit'
      AND object_id = OBJECT_ID(N'dbo.Activity_Log')
)
BEGIN
    CREATE INDEX IX_Activity_Log_DateInit
        ON dbo.Activity_Log (DateInit DESC, TaskName);
END
GO

CREATE OR ALTER PROCEDURE dbo.Activity_Log_Insert
(
    @localAdUser NVARCHAR(200),
    @userName    NVARCHAR(200) = NULL,
    @taskName    NVARCHAR(50)
)
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO dbo.Activity_Log (LocalADUser, UserName, TaskName)
    VALUES (
        LEFT(@localAdUser, 200),
        LEFT(NULLIF(LTRIM(RTRIM(@userName)), N''), 200),
        @taskName
    );
END
GO
