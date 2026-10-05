USE [OPERATIONSTOOL]
GO

IF OBJECT_ID(N'dbo.ObjectCatalog', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[ObjectCatalog] (
        [ObjectId]       INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [ObjectType]     VARCHAR(20)   NULL,
        [RLS_USER]       INT           NULL,
        [RLS_BU]         INT           NULL,
        [RLS_COU]        INT           NULL,
        [RLS_ROL]        INT           NULL,
        [ALLOW_EXECUTE]  INT           NULL,
        [ALLOW_CREATE]   INT           NULL,
        [ALLOW_READ]     INT           NULL,
        [ALLOW_UPDATE]   INT           NULL,
        [ALLOW_DELETE]   INT           NULL,
        [ObjectStatus]   VARCHAR(20)   NULL
    );
END
GO

IF OBJECT_ID(N'dbo.MenuCatalog', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[MenuCatalog] (
        [Id]        INT            NOT NULL PRIMARY KEY,
        [ParentId]  INT            NULL,
        [Name]      NVARCHAR(255)  NULL,
        [Url]       NVARCHAR(255)  NULL,
        [Icon]      NVARCHAR(255)  NULL,
        [ObjectId]  INT            NULL,
        [OrderMenu] INT            NULL
    );
END
GO

IF OBJECT_ID(N'dbo.ObjectPermission', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[ObjectPermission] (
        [PermissionId]    INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [ObjectId]        INT            NULL,
        [PermissionType]  NVARCHAR(20)   NULL,
        [PermissionValue] NVARCHAR(255)  NULL,
        [ALLOW_EXECUTE]   INT            NULL,
        [ALLOW_CREATE]    INT            NULL,
        [ALLOW_READ]      INT            NULL,
        [ALLOW_UPDATE]    INT            NULL,
        [ALLOW_DELETE]    INT            NULL
    );
END
GO

CREATE OR ALTER PROCEDURE [dbo].[Menu_Consult] (
    @userLogged NVARCHAR(255)
)
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @localAdUser NVARCHAR(255);

    SELECT TOP (1) @localAdUser = ufa.LocalADUser
    FROM MOSAIC.dbo.UsersForApps ufa
    WHERE ufa.ApplicationId = '9'
      AND (
            ufa.LocalADUser = @userLogged
         OR ufa.Mail = @userLogged
         OR ufa.UserName = @userLogged
      )
    ORDER BY CASE WHEN ufa.AppRolStatus = N'Active' THEN 0 ELSE 1 END, ufa.LocalADUser;

    SELECT mc.*
    FROM dbo.MenuCatalog mc
    INNER JOIN dbo.ObjectCatalog oc
        ON mc.ObjectId = oc.ObjectId
    WHERE oc.ObjectType = 'Menu'
      AND oc.ObjectId IN (
          SELECT DISTINCT op.ObjectId
          FROM dbo.ObjectPermission op
          WHERE
              (op.PermissionValue = @localAdUser AND op.ALLOW_READ = 1 AND op.PermissionType = 'User')
              OR
              op.PermissionValue IN (
                  SELECT DISTINCT CAST(ufa.RolId AS VARCHAR(10))
                  FROM MOSAIC.dbo.UsersForApps ufa
                  WHERE ufa.ApplicationId = '9'
                    AND ufa.AppRolStatus = N'Active'
                    AND ufa.LocalADUser = @localAdUser
              )
      );
END
GO

DECLARE @importObjectId INT;
DECLARE @dashboardObjectId INT;
DECLARE @missing NVARCHAR(4000);

DECLARE @names TABLE (
    UserName NVARCHAR(255) NOT NULL
);

INSERT INTO @names (UserName)
VALUES
    (N'Marta Collderram'),
    (N'David Cumplido'),
    (N'Joan Bandres'),
    (N'Pedro Muñoz'),
    (N'Chris Elliott'),
    (N'Brendan Poch');

SELECT @missing = STRING_AGG(n.UserName, N', ')
FROM @names n
WHERE NOT EXISTS (
    SELECT 1
    FROM MOSAIC.dbo.UsersForApps ufa
    WHERE ufa.ApplicationId = '9'
      AND ufa.AppRolStatus = N'Active'
      AND NULLIF(LTRIM(RTRIM(ufa.LocalADUser)), N'') IS NOT NULL
      AND ufa.UserName COLLATE Latin1_General_CI_AI = n.UserName COLLATE Latin1_General_CI_AI
);

IF @missing IS NOT NULL
BEGIN
    DECLARE @msg NVARCHAR(2048) = N'Menu seed stopped. Users not found on Operations Tool application 9: ' + @missing;
    THROW 50001, @msg, 1;
END

IF NOT EXISTS (SELECT 1 FROM dbo.MenuCatalog WHERE Url = N'import.html')
BEGIN
    INSERT INTO dbo.ObjectCatalog (
        ObjectType, RLS_USER, RLS_BU, RLS_COU, RLS_ROL,
        ALLOW_EXECUTE, ALLOW_CREATE, ALLOW_READ, ALLOW_UPDATE, ALLOW_DELETE, ObjectStatus
    )
    VALUES ('Menu', 1, 1, 1, 1, 1, 0, 1, 1, 0, 'Active');

    SET @importObjectId = SCOPE_IDENTITY();

    INSERT INTO dbo.MenuCatalog (Id, ParentId, Name, Url, Icon, ObjectId, OrderMenu)
    VALUES (
        ISNULL((SELECT MAX(Id) FROM dbo.MenuCatalog), 0) + 1,
        NULL,
        N'Import',
        N'import.html',
        N'fas fa-file-import',
        @importObjectId,
        1
    );
END
ELSE
BEGIN
    SELECT @importObjectId = ObjectId
    FROM dbo.MenuCatalog
    WHERE Url = N'import.html';
END

IF NOT EXISTS (SELECT 1 FROM dbo.MenuCatalog WHERE Url = N'dashboard.html')
BEGIN
    INSERT INTO dbo.ObjectCatalog (
        ObjectType, RLS_USER, RLS_BU, RLS_COU, RLS_ROL,
        ALLOW_EXECUTE, ALLOW_CREATE, ALLOW_READ, ALLOW_UPDATE, ALLOW_DELETE, ObjectStatus
    )
    VALUES ('Menu', 1, 1, 1, 1, 1, 0, 1, 1, 0, 'Active');

    SET @dashboardObjectId = SCOPE_IDENTITY();

    INSERT INTO dbo.MenuCatalog (Id, ParentId, Name, Url, Icon, ObjectId, OrderMenu)
    VALUES (
        ISNULL((SELECT MAX(Id) FROM dbo.MenuCatalog), 0) + 1,
        NULL,
        N'Dashboard',
        N'dashboard.html',
        N'fas fa-tachometer-alt',
        @dashboardObjectId,
        2
    );
END
ELSE
BEGIN
    SELECT @dashboardObjectId = ObjectId
    FROM dbo.MenuCatalog
    WHERE Url = N'dashboard.html';
END

IF NOT EXISTS (
    SELECT 1
    FROM dbo.ObjectPermission
    WHERE ObjectId = @importObjectId
      AND PermissionType = N'Rol'
      AND PermissionValue = N'10'
)
BEGIN
    INSERT INTO dbo.ObjectPermission (
        ObjectId, PermissionType, PermissionValue,
        ALLOW_EXECUTE, ALLOW_CREATE, ALLOW_READ, ALLOW_UPDATE, ALLOW_DELETE
    )
    VALUES (@importObjectId, N'Rol', N'10', 1, 1, 1, 1, 1);
END

INSERT INTO dbo.ObjectPermission (
    ObjectId, PermissionType, PermissionValue,
    ALLOW_EXECUTE, ALLOW_CREATE, ALLOW_READ, ALLOW_UPDATE, ALLOW_DELETE
)
SELECT DISTINCT
    @importObjectId,
    N'User',
    ufa.LocalADUser,
    1, 0, 1, 0, 0
FROM MOSAIC.dbo.UsersForApps ufa
INNER JOIN @names n
    ON ufa.UserName COLLATE Latin1_General_CI_AI = n.UserName COLLATE Latin1_General_CI_AI
WHERE ufa.ApplicationId = '9'
  AND ufa.AppRolStatus = N'Active'
  AND NULLIF(LTRIM(RTRIM(ufa.LocalADUser)), N'') IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM dbo.ObjectPermission op
      WHERE op.ObjectId = @importObjectId
        AND op.PermissionType = N'User'
        AND op.PermissionValue = ufa.LocalADUser
  );

INSERT INTO dbo.ObjectPermission (
    ObjectId, PermissionType, PermissionValue,
    ALLOW_EXECUTE, ALLOW_CREATE, ALLOW_READ, ALLOW_UPDATE, ALLOW_DELETE
)
SELECT DISTINCT
    @dashboardObjectId,
    N'Rol',
    CAST(ufa.RolId AS NVARCHAR(20)),
    1, 0, 1, 0, 0
FROM MOSAIC.dbo.UsersForApps ufa
WHERE ufa.ApplicationId = '9'
  AND ufa.AppRolStatus = N'Active'
  AND ufa.RolId IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM dbo.ObjectPermission op
      WHERE op.ObjectId = @dashboardObjectId
        AND op.PermissionType = N'Rol'
        AND op.PermissionValue = CAST(ufa.RolId AS NVARCHAR(20))
  );
GO
