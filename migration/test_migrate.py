import uuid

import migrate_mongo_to_postgres as migrator


def test_migration_withUnavailabilityServiceMongoId_setsMarkerOnThatRow() -> None:
    org_id = uuid.uuid4()
    marker_mongo_id = 99
    svc_map = {
        marker_mongo_id: uuid.uuid4(),
        100: uuid.uuid4(),
    }
    services = [
        {"id": marker_mongo_id, "name": "Unavailable"},
        {"id": 100, "name": "Gel Manicure"},
    ]

    rows = migrator.service_rows_for_insert(
        services,
        svc_map,
        org_id,
        unavailability_service_mongo_id=marker_mongo_id,
    )

    assert rows == [
        (svc_map[marker_mongo_id], org_id, "Unavailable", True, True),
        (svc_map[100], org_id, "Gel Manicure", True, False),
    ]


def test_migration_withoutArg_noRowsFlagged() -> None:
    org_id = uuid.uuid4()
    svc_map = {
        1: uuid.uuid4(),
        2: uuid.uuid4(),
    }
    services = [
        {"id": 1, "name": "Unavailable"},
        {"id": 2, "name": "Gel Manicure"},
    ]

    rows = migrator.service_rows_for_insert(
        services,
        svc_map,
        org_id,
        unavailability_service_mongo_id=None,
    )

    assert [row[4] for row in rows] == [False, False]
