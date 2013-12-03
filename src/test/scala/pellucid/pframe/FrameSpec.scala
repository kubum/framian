package pellucid.pframe

import org.specs2.mutable._

import spire.algebra._
import spire.std.string._
import spire.std.double._
import spire.std.int._
import spire.std.iterable._

import shapeless._

class FrameSpec extends Specification {
  val f0 = Frame.fromRows(
    "a" :: 1 :: HNil,
    "b" :: 2 :: HNil,
    "c" :: 3 :: HNil)
  val f1 = Frame.fromRows(
    "a" :: 3 :: HNil,
    "b" :: 2 :: HNil,
    "c" :: 1 :: HNil)
  val homogeneous = Frame.fromRows(
    1.0  :: 2.0 :: 3.0  :: HNil,
    0.5  :: 1.0 :: 1.5  :: HNil,
    0.25 :: 0.5 :: 0.75 :: HNil
  )
  val people = Frame.fromRows(
      "Bob"     :: 32 :: "Manager"  :: HNil,
      "Alice"   :: 24 :: "Employee" :: HNil,
      "Charlie" :: 44 :: "Employee"  :: HNil)
    .withColIndex(Index.fromKeys("Name", "Age", "Level"))
    .withRowIndex(Index.fromKeys("Bob", "Alice", "Charlie"))

  "Frame" should {
    "have sane equality" in {
      f0 must_== f0
      f0 must_!= f1
      f1 must_!= f0
      f0.columns(0).as[String].toFrame("abc") must_== f1.columns(0).as[String].toFrame("abc")
      f0.columns(1).as[Int].toFrame("123") must_!= f1.columns(1).as[Int].toFrame("123")
    }

    "have sane hashCode" in {
      f0.hashCode must_== f0.hashCode
      f0.hashCode must_!= f1.hashCode
      f1.hashCode must_!= f0.hashCode
      f0.columns(0).as[String].toFrame("abc").hashCode must_== f1.columns(0).as[String].toFrame("abc").hashCode
      f0.columns(1).as[Int].toFrame("123").hashCode must_!= f1.columns(1).as[Int].toFrame("123").hashCode
    }

    "order columns" in {
      people.orderColumns must_== Frame.fromRows(
          32 :: "Manager"  :: "Bob" :: HNil,
          24 :: "Employee" :: "Alice" :: HNil,
          44 :: "Employee"  :: "Charlie" :: HNil)
        .withColIndex(Index.fromKeys("Age", "Level", "Name"))
        .withRowIndex(Index.fromKeys("Bob", "Alice", "Charlie"))
    }

    "order rows" in {
      people.orderRows must_== Frame.fromRows(
          "Alice"   :: 24 :: "Employee" :: HNil,
          "Bob"     :: 32 :: "Manager"  :: HNil,
          "Charlie" :: 44 :: "Employee"  :: HNil)
        .withColIndex(Index.fromKeys("Name", "Age", "Level"))
        .withRowIndex(Index.fromKeys("Alice", "Bob", "Charlie"))
    }

    "use new row index" in {
      f0.withRowIndex(Index(0 -> 2, 1 -> 0, 2 -> 1)) must_== Frame.fromRows(
        "c" :: 3 :: HNil,
        "a" :: 1 :: HNil,
        "b" :: 2 :: HNil)
      f0.withRowIndex(Index(0 -> 0, 1 -> 0, 2 -> 0)) must_== Frame.fromRows(
        "a" :: 1 :: HNil,
        "a" :: 1 :: HNil,
        "a" :: 1 :: HNil)
      f0.withRowIndex(Index(0 -> 2)) must_== Frame.fromRows("c" :: 3 :: HNil)
      f0.withRowIndex(Index.empty[Int]) must_== Frame.empty[Int, Int].withColIndex(f0.colIndex)
    }

    "use new column index" in {
      f0.withColIndex(Index(0 -> 1, 1 -> 0)) must_== Frame.fromRows(
        1 :: "a" :: HNil,
        2 :: "b" :: HNil,
        3 :: "c" :: HNil)
      f0.withColIndex(Index(0 -> 0, 1 -> 0)) must_== Frame.fromRows(
        "a" :: "a" :: HNil,
        "b" :: "b" :: HNil,
        "c" :: "c" :: HNil)
      f0.withColIndex(Index(0 -> 1)) must_== Frame.fromRows(
        1 :: HNil,
        2 :: HNil,
        3 :: HNil)
      f0.withColIndex(Index.empty[Int]) must_== Frame.fromRows[HNil, Int](HNil, HNil, HNil)
    }

    "have trivial column/row representation for empty Frame" in {
      val frame = Frame.empty[String, String]
      frame.columnsAsSeries must_== Series.empty[String, UntypedColumn]
      frame.rowsAsSeries must_== Series.empty[String, UntypedColumn]
    }

    "be representable as columns" in {
      val series = f0.columnsAsSeries mapValues { col =>
        Series(f0.rowIndex, col.cast[Any](Frame.anyTypeable, implicitly))
      }

      series must_== Series(
        0 -> Series(0 -> "a", 1 -> "b", 2 -> "c"),
        1 -> Series(0 -> 1, 1 -> 2, 2 -> 3)
      )
    }

    "be representable as rows" in {
      val series = f0.rowsAsSeries mapValues { col =>
        Series(f0.colIndex, col.cast[Any](Frame.anyTypeable, implicitly))
      }

      series must_== Series(
        0 -> Series(0 -> "a", 1 -> 1),
        1 -> Series(0 -> "b", 1 -> 2),
        2 -> Series(0 -> "c", 1 -> 3)
      )
    }
  }

  "Frame joins" should {
    "inner join with empty frame" in {
      val e = Frame.empty[Int, Int]
      f0.join(e)(Join.Inner) must_== f0.withRowIndex(Index.empty[Int])
      e.join(f0)(Join.Inner) must_== f0.withRowIndex(Index.empty[Int])
      e.join(e)(Join.Inner) must_== e
    }

    "inner join with self" in {
      f0.join(f0)(Join.Inner) must_== Frame.fromRows(
          "a" :: 1 :: "a" :: 1 :: HNil,
          "b" :: 2 :: "b" :: 2 :: HNil,
          "c" :: 3 :: "c" :: 3 :: HNil)
        .withColIndex(Index.fromKeys(0, 1, 0, 1))
    }

    "inner join only matching rows" in {
      val a = Frame.fromRows(1 :: HNil, 2 :: HNil)
        .withRowIndex(Index.fromKeys("a", "b"))
      val b = Frame.fromRows(2.0 :: HNil, 3.0 :: HNil)
        .withRowIndex(Index.fromKeys("b", "c"))
      val c = Frame.fromRows(2 :: 2.0 :: HNil)
        .withRowIndex(Index.fromKeys("b"))
        .withColIndex(Index.fromKeys(0, 0))

      a.join(b)(Join.Inner) must_== c
    }

    "inner join forms cross-product of matching rows" in {
      val a = Frame.fromRows(1 :: HNil, 2 :: HNil)
        .withRowIndex(Index.fromKeys("a", "a"))
      val b = Frame.fromRows(2.0 :: HNil, 3.0 :: HNil)
        .withRowIndex(Index.fromKeys("a", "a"))
      val c = Frame.fromRows(
        1 :: 2.0 :: HNil,
        1 :: 3.0 :: HNil,
        2 :: 2.0 :: HNil,
        2 :: 3.0 :: HNil)
        .withRowIndex(Index.fromKeys("a", "a", "a", "a"))
        .withColIndex(Index.fromKeys(0, 0))

      a.join(b)(Join.Inner) must_== c
    }

    "left join keeps left mismatched rows" in {
      val a = Frame.fromRows(1 :: HNil, 2 :: HNil)
        .withRowIndex(Index.fromKeys("a", "b"))
      val b = Frame.fromRows(2.0 :: HNil, 3.0 :: HNil)
        .withRowIndex(Index.fromKeys("b", "c"))
      val c = Frame[String, Int](Index.fromKeys("a", "b"),
        0 -> TypedColumn(Column.fromCells(Vector(Value(1), Value(2)))),
        0 -> TypedColumn(Column.fromCells(Vector(NA, Value(2.0)))))
      a.join(b)(Join.Left) must_== c
    }

    "left join with empty frame" in {
      val a = Frame.fromRows(1 :: HNil, 2 :: HNil)
        .withRowIndex(Index.fromKeys("a", "b"))
      val e = Frame.empty[String, Int]
      a.join(e)(Join.Left) must_== a
      e.join(a)(Join.Left) must_== e.withColIndex(Index.fromKeys(0))
    }

    "right join keeps right mismatched rows" in {
      val a = Frame.fromRows(1 :: HNil, 2 :: HNil)
        .withRowIndex(Index.fromKeys("a", "b"))
      val b = Frame.fromRows(2.0 :: HNil, 3.0 :: HNil)
        .withRowIndex(Index.fromKeys("b", "c"))
      val c = Frame[String, Int](Index.fromKeys("b", "c"),
        0 -> TypedColumn(Column.fromCells(Vector(Value(2), NA))),
        0 -> TypedColumn(Column.fromCells(Vector(Value(2.0), Value(3.0)))))
      a.join(b)(Join.Right) must_== c
    }

    "right join with empty frame" in {
      val a = Frame.fromRows(1 :: HNil, 2 :: HNil)
        .withRowIndex(Index.fromKeys("a", "b"))
      val e = Frame.empty[String, Int]
      a.join(e)(Join.Right) must_== e.withColIndex(Index.fromKeys(0))
      e.join(a)(Join.Right) must_== a
    }

    "outer join keeps all rows" in {
      val a = Frame.fromRows(1 :: HNil, 2 :: HNil)
        .withRowIndex(Index.fromKeys("a", "b"))
      val b = Frame.fromRows(2.0 :: HNil, 3.0 :: HNil)
        .withRowIndex(Index.fromKeys("b", "c"))
      val c = Frame[String, Int](Index.fromKeys("a", "b", "c"),
        0 -> TypedColumn(Column.fromCells(Vector(Value(1), Value(2), NA))),
        0 -> TypedColumn(Column.fromCells(Vector(NA, Value(2.0), Value(3.0)))))
      a.join(b)(Join.Outer) must_== c
    }

    "outer join with empty frame" in {
      val a = Frame.fromRows(1 :: HNil, 2 :: HNil)
        .withRowIndex(Index.fromKeys("a", "b"))
      val e = Frame.empty[String, Int]
      a.join(e)(Join.Outer) must_== a
      e.join(a)(Join.Outer) must_== a
    }
  }

  // "ColumnSelection" should {
  // }
}