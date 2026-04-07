package app.subconsciously.helper

import app.subconsciously.data.AppModel

interface AppFilterHelper {
    fun onAppFiltered(items:List<AppModel>)
}