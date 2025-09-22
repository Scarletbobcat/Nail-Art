import pymongo
import datetime
import os
from dotenv import load_dotenv

load_dotenv()

connection_string = os.getenv("MONGO_URI")

client = pymongo.MongoClient(connection_string)

db = client["Nail-Art"]

appointments = db["Appointments"]
archived_appointments = db["ArchivedAppointments"]

today = datetime.date.today()

two_weeks_ago = today - datetime.timedelta(days=14)

past_appointments = appointments.find({"date": {"$lt": str(two_weeks_ago)}}).to_list()

month_ago = today - datetime.timedelta(days=30)

appoinments_greater_than_month = archived_appointments.find({"date": {"$lt": str(month_ago)}}).to_list()

if (past_appointments):
  print("Archiving appointments...")
  # inserting into archive collection
  archived_appointments.insert_many(past_appointments)
  # removing from appointments collection
  appointments.delete_many({"date": {"$lt": str(today)}})
  print("Archived appointments successfully!")
else:
  print("No appointments to archive.")

if (appoinments_greater_than_month):
  print("Removing appointments older than a month...")
  archived_appointments.delete_many({"date": {"$lt": str(month_ago)}})
  print("Removed appointments older than a month successfully!")
else:
  print("No archived appointments to remove.")