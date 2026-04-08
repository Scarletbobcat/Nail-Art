import pymongo
import os
from dotenv import load_dotenv

load_dotenv()

connection_string = os.getenv("MONGO_URI")
db_name = os.getenv("MONGO_DB", "Nail-Art")

mongo_client = pymongo.MongoClient(connection_string)

db = mongo_client[db_name]

clients = db["Clients"]
appointments = db["Appointments"]
archived_appointments = db["ArchivedAppointments"]
counters = db["Counters"]

# --- Pre-migration state ---
total_clients = clients.count_documents({})
print(f"Total clients before migration: {total_clients}")

counter_doc = counters.find_one({"collectionName": "Clients"})
print(f"Clients counter before migration: {counter_doc['sequence'] if counter_doc else 'NOT FOUND'}")

# --- Find duplicates ---
pipeline = [
    {"$match": {"phoneNumber": {"$ne": None, "$ne": ""}}},
    {"$group": {
        "_id": "$phoneNumber",
        "count": {"$sum": 1},
        "clients": {"$push": {"_id": "$_id", "id": "$id", "name": "$name", "phoneNumber": "$phoneNumber"}}
    }},
    {"$match": {"count": {"$gt": 1}}},
    {"$sort": {"count": -1}}
]

duplicates = list(clients.aggregate(pipeline))

if not duplicates:
    print("No duplicate clients found. Nothing to do.")
else:
    print(f"Found {len(duplicates)} duplicate phone numbers to merge.\n")

    for dup in duplicates:
        phone = dup["_id"]
        group = sorted(dup["clients"], key=lambda c: c["id"])
        keeper = group[0]
        victims = group[1:]

        # Pick the longer name between keeper and victims
        best_name = keeper["name"]
        for v in victims:
            if v["name"] and len(v["name"]) > len(best_name or ""):
                best_name = v["name"]

        if best_name != keeper["name"]:
            print(f"  [{phone}] Name: keeping \"{best_name}\" (from victim) over \"{keeper['name']}\" (keeper)")
        else:
            victim_names = [v["name"] for v in victims]
            print(f"  [{phone}] Name: keeping \"{best_name}\" (keeper). Victim names: {victim_names}")

        # Update keeper's name if a longer one was found
        if best_name != keeper["name"]:
            clients.update_one(
                {"_id": keeper["_id"]},
                {"$set": {"name": best_name}}
            )

        for victim in victims:
            with mongo_client.start_session() as session:
                with session.start_transaction():
                    # Update Appointments referencing the victim
                    appt_result = appointments.update_many(
                        {"clientId": victim["id"]},
                        {"$set": {
                            "clientId": keeper["id"],
                            "name": best_name,
                            "phoneNumber": keeper["phoneNumber"]
                        }},
                        session=session
                    )
                    print(f"  [{phone}] Updated {appt_result.modified_count} appointments from client {victim['id']} -> {keeper['id']}")

                    # Update ArchivedAppointments referencing the victim
                    archived_result = archived_appointments.update_many(
                        {"clientId": victim["id"]},
                        {"$set": {
                            "clientId": keeper["id"],
                            "name": best_name,
                            "phoneNumber": keeper["phoneNumber"]
                        }},
                        session=session
                    )
                    print(f"  [{phone}] Updated {archived_result.modified_count} archived appointments from client {victim['id']} -> {keeper['id']}")

                    # Delete the victim client
                    clients.delete_one({"_id": victim["_id"]}, session=session)
                    print(f"  [{phone}] Deleted victim client {victim['id']} (\"{victim['name']}\")")

        # Reconstruct keeper's appointmentIds from actual Appointments
        actual_appt_ids = [
            doc["id"] for doc in appointments.find({"clientId": keeper["id"]}, {"id": 1})
        ]
        clients.update_one(
            {"_id": keeper["_id"]},
            {"$set": {"appointmentIds": actual_appt_ids}}
        )
        print(f"  [{phone}] Reconstructed appointmentIds for client {keeper['id']}: {len(actual_appt_ids)} appointments")
        print()

# --- Re-sync Clients counter to max(Client.id) ---
max_id_result = list(clients.aggregate([
    {"$group": {"_id": None, "maxId": {"$max": "$id"}}}
]))

if max_id_result:
    max_client_id = max_id_result[0]["maxId"]
    counters.update_one(
        {"collectionName": "Clients"},
        {"$max": {"sequence": max_client_id}},
        upsert=True
    )
    print(f"Clients counter synced to max(Client.id) = {max_client_id}")

# --- Verify no duplicates remain ---
remaining = list(clients.aggregate(pipeline))
if remaining:
    print(f"\nWARNING: {len(remaining)} duplicate phone numbers still exist!")
    for r in remaining:
        print(f"  {r['_id']}: {r['count']} clients")
else:
    print("\nVerified: zero duplicate phone numbers remain.")

# --- Create partial filter unique index ---
index_name = "phoneNumber_unique_partial"
existing_indexes = clients.index_information()

if index_name in existing_indexes:
    print(f"Index '{index_name}' already exists. Skipping creation.")
else:
    clients.create_index(
        "phoneNumber",
        unique=True,
        name=index_name,
        partialFilterExpression={"phoneNumber": {"$type": "string", "$gt": ""}}
    )
    print(f"Created partial filter unique index '{index_name}' on Clients.phoneNumber")

# --- Final state ---
total_clients_after = clients.count_documents({})
counter_doc_after = counters.find_one({"collectionName": "Clients"})
print(f"\nTotal clients after migration: {total_clients_after}")
print(f"Clients counter after migration: {counter_doc_after['sequence'] if counter_doc_after else 'NOT FOUND'}")
print("Done.")
