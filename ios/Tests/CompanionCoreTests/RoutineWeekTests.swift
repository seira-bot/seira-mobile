import XCTest
@testable import CompanionCore

final class RoutineWeekTests: XCTestCase {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Kolkata")!
        return calendar
    }()

    /// Monday 5 Oct 2026, 00:00 in Kolkata.
    private var monday: Date { calendar.date(from: DateComponents(year: 2026, month: 10, day: 5))! }

    private func at(_ day: Date, _ hour: Int) -> Date {
        calendar.date(bySettingHour: hour, minute: 0, second: 0, of: day)!
    }

    private func ms(_ date: Date) -> Double { date.timeIntervalSince1970 * 1_000 }

    private func routine(_ id: String, schedule: String) throws -> Routine {
        let created = ms(calendar.date(byAdding: .day, value: -30, to: monday)!)
        let json = """
        {"id":"\(id)","name":"\(id) name","prompt":"p","botId":"b1","runOn":"maus","enabled":true,
         "schedule":\(schedule),"durationMinutes":30,"nextRunAt":null,"createdAt":\(created),"updatedAt":\(created)}
        """
        return try JSONDecoder().decode(Routine.self, from: Data(json.utf8))
    }

    private func run(_ id: String, routine: String, at date: Date, status: String) throws -> RoutineRun {
        let json = """
        {"id":"\(id)","routineId":"\(routine)","routineName":"\(routine) name","botId":"b1","runOn":"maus",
         "scheduledFor":\(ms(date)),"status":"\(status)","manual":false,"createdAt":\(ms(date))}
        """
        return try JSONDecoder().decode(RoutineRun.self, from: Data(json.utf8))
    }

    func testTheWeekListCoversSevenDaysFromTheGivenDay() {
        let days = RoutineCalendar.nextSevenDays(from: monday, calendar: calendar)
        XCTAssertEqual(days.count, 7)
        XCTAssertEqual(days.first, monday)
        XCTAssertEqual(calendar.component(.day, from: days[6]), 11)
    }

    func testRunsAndProjectionsComeOldestFirstWithTheirStatus() throws {
        let brief = try routine("brief", schedule: #"{"type":"daily","time":"09:00","weekdays":[0,1,2,3,4,5,6]}"#)
        let tuesday = calendar.date(byAdding: .day, value: 1, to: monday)!
        let runs = [
            try run("r1", routine: "brief", at: at(monday, 9), status: "completed"),
            try run("r2", routine: "brief", at: at(tuesday, 9), status: "failed"),
        ]
        let rows = RoutineCalendar.weekRows(routines: [brief], runs: runs, from: monday, calendar: calendar)
        XCTAssertEqual(rows.count, 7)
        XCTAssertEqual(rows.map(\.item.at), rows.map(\.item.at).sorted())
        XCTAssertEqual(rows[0].status, .completed)
        XCTAssertEqual(rows[1].status, .failed)
        XCTAssertTrue(rows.dropFirst(2).allSatisfy { $0.status == .scheduled })
    }

    func testARunStillMovingIsNeitherCompletedNorFailed() throws {
        let brief = try routine("brief", schedule: #"{"type":"daily","time":"09:00","weekdays":[1]}"#)
        let running = try run("r1", routine: "brief", at: at(monday, 9), status: "running")
        let rows = RoutineCalendar.weekRows(routines: [brief], runs: [running], from: monday, calendar: calendar)
        XCTAssertEqual(rows.map(\.status), [.other])
    }

    func testRunsOutsideTheSevenDaysAreLeftOut() throws {
        let before = try run("r1", routine: "brief", at: at(calendar.date(byAdding: .day, value: -1, to: monday)!, 9), status: "completed")
        let after = try run("r2", routine: "brief", at: at(calendar.date(byAdding: .day, value: 7, to: monday)!, 9), status: "completed")
        XCTAssertTrue(RoutineCalendar.weekRows(routines: [], runs: [before, after], from: monday, calendar: calendar).isEmpty)
    }
}
