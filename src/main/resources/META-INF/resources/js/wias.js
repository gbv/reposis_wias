
$(document).ready(function() {

// spam protection for mails
  $('span.madress').each(function(i) {
      var text = $(this).text();
      var address = text.replace(" [at] ", "@");
      $(this).after('<a href="mailto:'+address+'">'+ address +'</a>')
      $(this).remove();
  });


  var wiasConf = window["wiasConfig"] || {};

  $("#submit_publication .btn-primary").on("click", function(e){
      e.preventDefault();
      var selectedGenre = $( "#genre option:selected" ).val();

      switch(selectedGenre) {
        case "preprint":
          $('#submit_publication').append('<input type="hidden" name="relatedItemId" value="' + wiasConf.preprintSeriesId + '" />');
          $('#submit_publication').append('<input type="hidden" name="relatedItemType" value="series" />');
          $('#submit_publication').submit();
          break;
        case "report":
          $('#submit_publication').append('<input type="hidden" name="relatedItemId" value="' + wiasConf.reportSeriesId + '" />');
          $('#submit_publication').append('<input type="hidden" name="relatedItemType" value="series" />');
          $('#submit_publication').submit();
          break;
        case "technical_report":
          $('#submit_publication').append('<input type="hidden" name="relatedItemId" value="' + wiasConf.techReportSeriesId + '" />');
          $('#submit_publication').append('<input type="hidden" name="relatedItemType" value="series" />');
          $('#submit_publication').submit();
          break;
        default:
          $('#submit_publication').submit();
      }

  });
  $('select[class*="autocomplete"]').selectpicker({
    liveSearch:true,
    liveSearchNormalize:true,
    virtualScroll:true,
    showSubtext:true,
    size:10,
    dropupAuto: false
  });

  // --- Preprint series: lock volume field ---
  var PREPRINT_SERIES_ID = wiasConf.preprintSeriesId || '';

  function getSeriesVolumeInput() {
    return $('#relItem-series').closest('fieldset.mir-relatedItem')
      .find('.mir-modspart input[type="text"]');
  }

  function updateVolumeForSeries(seriesId) {
    var volumeInput = getSeriesVolumeInput();
    if (volumeInput.length === 0) {
      return;
    }
    if (seriesId === PREPRINT_SERIES_ID) {
      volumeInput.val('');
      volumeInput.prop('disabled', true);
    } else {
      volumeInput.prop('disabled', false);
    }
  }

  // Hook into fillFieldset so volume is updated after series data is loaded
  if (typeof fillFieldset === 'function') {
    var originalFillFieldset = fillFieldset;
    fillFieldset = function(fieldset, xml) {
      originalFillFieldset(fieldset, xml);
      var seriesInput = fieldset.find('#relItem-series');
      if (seriesInput.length > 0) {
        updateVolumeForSeries(seriesInput.val());
      }
    };
  }


});