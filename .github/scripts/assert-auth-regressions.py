"""Release CI dùng kết quả Maven verify trên PostgreSQL riêng, giữ từng nhóm kiểm tra sau refactor auth."""
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as XML

groups = {
    'account': ['AccountSecurityTest', 'AccountMailTemplateTest', 'BusinessIntegrationTest'],
    'managed-accounts': ['ManagedAccountSecurityTest', 'BusinessIntegrationTest'],
    'foundation': ['FoundationSecurityTest', 'FoundationRulesTest', 'SystemAuditTest', 'SystemManagementTest', 'OpenApiContractTest', 'ApiJournalTest', 'SchemaMigrationTest'],
    'access-tree': ['AccessTreeTest', 'AccessPermissionDefinitionsTest'],
    'database-guard': ['EndpointSecurityTest', 'DatabaseAccessGuardTest', 'DirectServiceAuthorityTest'],
    'data-scope': ['DataScopeSecurityTest', 'BusinessIntegrationTest'],
    'access-history': ['AccessChangeHistoryTest', 'BusinessIntegrationTest'],
    'access-definitions': ['AccessResourceDiscoveryTest', 'AccessRegistrySecurityTest'],
    'categories': ['CategorySecurityTest', 'BusinessIntegrationTest'],
    'access-registry': ['AccessRegistrySecurityTest', 'BusinessIntegrationTest'],
    'access-cost': ['BusinessIntegrationTest'],
    'business-load': ['BusinessIntegrationTest'],
    'admin-recovery': ['AdminRecoverySecurityTest', 'AccountSecurityTest'],
}

if os.environ.get('GITHUB_ACTIONS') != 'true':
    raise SystemExit('Chỉ release CI sau Maven verify với PostgreSQL riêng; chạy mvnw verify để nghiệm thu local')

for name in groups[sys.argv[1]]:
    report = Path('target/surefire-reports') / ('TEST-vn.vietroute.' + name + '.xml')
    suite = XML.parse(report).getroot()
    assert int(suite.attrib['tests']) > 0, name
    for key in ('failures', 'errors', 'skipped'):
        assert int(suite.attrib.get(key, 0)) == 0, f'{name}: {key}'
    if name == 'BusinessIntegrationTest':
        cases = {value.attrib['name'] for value in suite.findall('testcase')}
        required = {'allEightJpaModelsMapEveryRealColumnIncludingGeneratedScopeKeys',
                    'jpaUserCrudAndJdbcHistoryShareRollbackAndOptimisticVersion',
                    'roleGrantSnapshotAndMembershipHistoryStayBoundedAndBatchFailureRollsBackAllEntries',
                    'inactiveAllScopesRoleStillProtectsFunctionFromScopedEditor',
                    'nativeScopeCannotBorrowDetailEndpointFromRoleOutsideThatScope',
                    'checksumMismatchStopsBeforeChangingBusinessOrCredentials',
                    'unexpectedHistoryRequiresExplicitOfflineRebaseline',
                    'baselineStartupIsIdempotentAndPreservesCredentialsAndBusiness'}
        required.update({'completeInitializationCreatesRunnableAccountsTripsAndBookingsWithCorrectScopes',
                         'completeInitializationIsIdempotentAndKeepsUserChangesAndRevokedScope'})
        required.update({'duyLongTimetableKeepsFourDailyLegsSixteenSeatsAndStableAnchor',
                         'duyLongAccountsCanSearchBookConfirmAndPayWithoutCrossCarrierAccess'})
        required.update({'categoryTreeLoadsDirectChildrenAndDeniesRemovedEndpoints',
                         'categoryUpgradeRetiresOnlyOldResourcesAndKeepsRevokedRolesInactive'})
        assert required <= cases, 'Thiếu regression endpoint/JPA/batch/scope bắt buộc'
print('Regression ' + sys.argv[1] + ': đủ ca kiểm, không fail/error/skip')
