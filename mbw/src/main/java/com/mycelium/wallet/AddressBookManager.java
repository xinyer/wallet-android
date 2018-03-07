package com.mycelium.wallet;

import java.util.UUID;

import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

import com.mrd.bitlib.model.Address;

public class AddressBookManager {
   public static class Entry implements Comparable<Entry> {
      private Address _address;
      private String _name;

      public Entry(Address address, String name) {
         _address = address;
         _name = name == null ? "" : name;
      }

      public Address getAddress() {
         return _address;
      }

      public String getName() {
         return _name;
      }

      @Override
      public int compareTo(@NonNull Entry another) {
         return _name.compareToIgnoreCase(another._name);
      }

      @Override
      public int hashCode() {
         return _name.hashCode() + _address.hashCode();
      }

      @Override
      public boolean equals(Object obj) {
         if (!(obj instanceof Entry)) {
            return false;
         }
         Entry other = (Entry) obj;
         return _address.equals(other._address) && _name.equals(other._name);
      }
   }

   public static class IconEntry extends Entry{
      private Drawable _icon;
      private UUID id;

      public IconEntry(Address address, String name, Drawable icon) {
         super(address, name);
         this._icon = icon;
      }

      public IconEntry(Address address, String name,  Drawable icon, UUID id) {
         this(address, name, icon);
         this.id = id;
      }

      public UUID getId() {
         return id;
      }

      public Drawable getIcon() {
         return _icon;
      }
   }
}
