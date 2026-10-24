"""Execute the production migration SQL using SQLite without an Android device."""
from pathlib import Path
import re
import sqlite3
import unittest

LOCAL = Path(__file__).resolve().parents[2] / 'app/src/main/java/com/kairo/reader/data/local'


class MetadataMigrationTest(unittest.TestCase):
    def test_oversized_existing_rows_remain_readable_without_losing_content(self):
        constants = dict(re.findall(r'const val (MAX_METADATA_\w+) = (\d+)',
                                    (LOCAL / 'MetadataLimits.kt').read_text()))
        migration = (LOCAL / 'Migrations.kt').read_text().split('val MIGRATION_14_15:', 1)[1]
        statements = re.findall(r'"""(.*?)"""', migration, re.S)
        self.assertEqual(3, len(statements))
        with sqlite3.connect(':memory:') as database:
            database.executescript('''
                CREATE TABLE books (id TEXT PRIMARY KEY, title TEXT, authors TEXT, languageTag TEXT);
                CREATE TABLE chapters (bookId TEXT, title TEXT, plainText TEXT);
                CREATE TABLE table_of_contents_entries (bookId TEXT, label TEXT, characterOffset INTEGER);
            ''')
            oversized = '界' * 1_048_576
            database.execute('INSERT INTO books VALUES (?, ?, ?, ?)', ('large', oversized, oversized, oversized))
            database.execute('INSERT INTO books VALUES (?, ?, ?, ?)', ('normal', 'Title', 'Ada|||Grace', None))
            database.execute('INSERT INTO chapters VALUES (?, ?, ?)', ('large', oversized, 'Unchanged body'))
            database.execute('INSERT INTO table_of_contents_entries VALUES (?, ?, ?)', ('large', oversized, 42))
            for _ in range(2):
                for statement in statements:
                    statement = re.sub(r'\$(MAX_METADATA_\w+)', lambda match: constants[match[1]], statement)
                    database.execute(statement)
            title, authors, language = database.execute(
                "SELECT title, authors, languageTag FROM books WHERE id = 'large'").fetchone()
            self.assertEqual((4096, 16384, 128), (len(title), len(authors), len(language)))
            self.assertLess(len((title + authors + language).encode()), 100_000)
            self.assertEqual(('Title', 'Ada|||Grace', None), database.execute(
                "SELECT title, authors, languageTag FROM books WHERE id = 'normal'").fetchone())
            self.assertEqual((4096, 'Unchanged body'), database.execute(
                'SELECT length(title), plainText FROM chapters').fetchone())
            self.assertEqual((4096, 42), database.execute(
                'SELECT length(label), characterOffset FROM table_of_contents_entries').fetchone())


if __name__ == '__main__':
    unittest.main()
