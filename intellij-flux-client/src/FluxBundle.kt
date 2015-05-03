package org.intellij.flux

import com.intellij.CommonBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.ResourceBundle

object FluxBundle {
  public fun message(PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return CommonBundle.message(getBundle(), key, *params)
  }

  private var ourBundle: Reference<ResourceBundle>? = null

  NonNls
  public val BUNDLE: String = "FluxBundle"

  private fun getBundle(): ResourceBundle {
    var bundle = com.intellij.reference.SoftReference.dereference<ResourceBundle>(ourBundle)
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE)
      ourBundle = SoftReference(bundle)
    }
    return bundle
  }
}
