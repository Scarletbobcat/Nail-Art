import { DayPilotNavigator, DayPilot } from "@daypilot/daypilot-lite-react";

export default function CalendarNavigator({
  startDate,
  setStartDate,
}: {
  startDate: DayPilot.Date;
  setStartDate: (date: DayPilot.Date) => void;
}) {
  return (
    <div>
      <DayPilotNavigator
        selectMode="Day"
        showMonths={1}
        startDate={startDate}
        selectionDay={startDate}
        onTimeRangeSelected={(args: { day: DayPilot.Date }) =>
          setStartDate(args.day)
        }
      />
    </div>
  );
}
