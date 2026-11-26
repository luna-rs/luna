package game.content.godBooks

import api.predef.*

for (book in GodBook.entries) {
    item3(book.damaged) { book.check(plr) }
    item3(book.complete) { book.preach(plr) }
    book.pages.forEachIndexed { index, page ->
        useItem(page).onItem(book.damaged) { book.addPage(plr, index + 1) }
    }
}
