import pymongo
import datetime
import os
from dotenv import load_dotenv

load_dotenv()

connection_string = os.getenv("MONGO_URI")

client = pymongo.MongoClient(connection_string)

db = client["Nail-Art"]

appointments = db["Appointments"]
# db.create_collection("ArchivedAppointments")
archived_appointments = db["ArchivedAppointments"]

today = datetime.date.today()

past_appointments = appointments.find({"date": {"$lt": str(today)}})

if (past_appointments):
  print("Archiving appointments...")
  # inserting into archive collection
  archived_appointments.insert_many(past_appointments)
  # removing from appointments collection
  appointments.delete_many({"date": {"$lt": str(today)}})
  print("Archived appointments successfully!")